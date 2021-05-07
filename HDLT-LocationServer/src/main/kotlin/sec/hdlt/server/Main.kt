package sec.hdlt.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.grpc.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jooq.SQLDialect
import org.jooq.exception.DataAccessException
import org.jooq.impl.DefaultConfiguration
import sec.hdlt.protos.server.*
import sec.hdlt.protos.server.Server
import sec.hdlt.protos.server2server.ReadGrpcKt
import sec.hdlt.protos.server2server.Server2Server
import sec.hdlt.protos.server2server.WriteGrpcKt
import sec.hdlt.server.dao.AbstractDAO
import sec.hdlt.server.dao.NonceDAO
import sec.hdlt.server.dao.ReportDAO
import sec.hdlt.server.domain.*
import sec.hdlt.server.services.*
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.spec.KeySpec
import java.util.*
import java.util.logging.Logger
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

const val KEY_USER_PREFIX = "cert_hdlt_user_"
const val KEY_HA_ALIAS = "hdlt_ha"
const val KEY_SERVER_PREFIX = "hdlt_server_"
const val CERT_SERVER_PREFIX = "cert_$KEY_SERVER_PREFIX"
const val KEY_SERVER_PASS = "123"
const val KEYSTORE_FILE = "/server.jks"
const val KEYSTORE_PASS = "KeyStoreServer"

const val BASE_PORT = 7777
const val MAX_GRPC_TIME = 60L // seconds

const val PBKDF2_ITER = 100001
const val PBKDF2_KEY_SIZE = 512
const val PASS_PREFIX = "server_pass_id_"
const val PASS_SALT = "secret_salt"

var F = 0
var FLINE = 0
val logger = Logger.getLogger("LocationServer")

fun initDatabaseDaos(serverPort: Int): Map<String, AbstractDAO> {
    val dbConfig = DefaultConfiguration()
        .set(SQLDialect.SQLITE)
        .set(HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:src/main/resources/db/database$serverPort.sqlite"
            maximumPoolSize = 15
        }))
    return mapOf("report" to ReportDAO(dbConfig), "userNonces" to NonceDAO(dbConfig))
}

fun checkDatabaseFile(serverPort: Int) {
    val dbFile = Paths.get("src/main/resources/db/database$serverPort.sqlite")
    if (!Files.exists(dbFile)) {
        val templateFile = Paths.get("src/main/resources/db/template.database.sqlite")
        Files.copy(templateFile, dbFile, StandardCopyOption.REPLACE_EXISTING)
    }
}

fun main(args: Array<String>) {
    println("************************")
    println("* HDLT Location Server *")
    println("************************")

    if (args.size != 2) {
        println("Usage: server <server id> <byzantine level>")
        return
    }

    val serverId: Int
    try {
        serverId = args[0].toInt()
    } catch (e: NumberFormatException) {
        println("Invalid server id")
        return
    }
    val serverPort = BASE_PORT + serverId

    checkDatabaseFile(serverPort)

    val byzantineLevel: Int = args[1].toIntOrNull() ?: -1

    // Load the keystore
    val keyStore = KeyStore.getInstance("jks")
    val keystoreFile: InputStream = object {}.javaClass.getResourceAsStream(KEYSTORE_FILE)!!
    loadServerSettings()

    val daos = initDatabaseDaos(serverPort)
    val reportDao = daos["report"] as ReportDAO
    val nonceDao = daos["userNonces"] as NonceDAO

    try {
        keyStore.load(keystoreFile, KEYSTORE_PASS.toCharArray())
    } catch (e: IOException) {
        println("Couldn't open KeyStore file")
        return
    } catch (e: CertificateException) {
        println("Couldn't load all keys/certificates")
        return
    }

    val serverKey: PrivateKey = keyStore.getKey("KEY_SERVER_PREFIX$serverId", deriveKey(PASS_PREFIX + serverId).toCharArray()) as PrivateKey

    Database(keyStore, serverKey, reportDao, nonceDao)

    val server = ServerBuilder.forPort(serverPort).apply {
        addService(Location())
        addService(Setup())
        addService(HA())
        addService(ServerWrite())
        addService(ServerRead())
    }.build()

    CommunicationService.initValues(serverId, F)

    server.start()
    server.awaitTermination()
}

fun loadServerSettings() {
    try {
        val file = File("server.settings")
        val line = file.readLines().first()
        val split = line.split(" ")
        F = split[0].toInt()
        FLINE = split[1].toInt()
    } catch (e: Exception) {
        logger.severe("Couldn't load server settings to file")
    }
}

class Setup : SetupGrpcKt.SetupCoroutineImplBase() {
    override suspend fun broadcastValues(request: Server.BroadcastValuesRequest): Server.BroadcastValuesResponse {
        F = request.f
        FLINE = request.fLine
        try {
            val file = File("server.settings")
            file.writeText("$F $FLINE")
        } catch (e: Exception) {
            logger.severe("Couldn't save server settings to file")
        }

        return Server.BroadcastValuesResponse.newBuilder().apply {
            ok = true
        }.build()
    }
}

class Location : LocationGrpcKt.LocationCoroutineImplBase() {
    override suspend fun submitLocationReport(request: Report.ReportRequest): Report.ReportResponse {
        val report: LocationReport =
            requestToLocationReport(Database.key, request.nonce, request.key, request.ciphertext)

        val user = report.id
        val epoch = report.epoch
        val coordinates = report.location
        val sig = report.signature
        var proofs = report.proofs

        if (RequestValidationService.validateSignature(
                user,
                epoch,
                coordinates,
                sig
            ) && !LocationReportService.hasReport(user, epoch)
        ) {
            proofs = RequestValidationService.getValidProofs(user, epoch, proofs)

            if (proofs.isNotEmpty()) {
                val ack = LocationReportService.storeLocationReport(report, epoch, user, coordinates, proofs)
                return Report.ReportResponse.newBuilder().apply {
                    this.ack = ack
                }.build()
            }
        }

        return Report.ReportResponse.newBuilder().apply {
            ack = false
        }.build()
    }

    override suspend fun getLocationReport(request: Report.UserLocationReportRequest): Report.UserLocationReportResponse {
        val symKey: SecretKey = asymmetricDecipher(Database.key, request.key)
        val locationRequest: LocationRequest = requestToLocationRequest(symKey, request.nonce, request.ciphertext)
        val user = locationRequest.id
        val epoch = locationRequest.epoch
        val sig = locationRequest.signature

        val decipheredNonce = Base64.getDecoder().decode(request.nonce)
        val validNonce = try {
            Database.nonceDAO.storeUserNonce(decipheredNonce, user)
        } catch (e: DataAccessException) {
            false
        }

        if (!validNonce || !RequestValidationService.validateSignature(user, epoch, sig)) {
            return Report.UserLocationReportResponse.getDefaultInstance()
        }

        val locationReport = LocationReportService.getLocationReport(user, epoch, F)
        return if (locationReport != null) {
            locationReport.signature = sign(
                Database.key,
                "${locationReport.id}${locationReport.epoch}${locationReport.coords}${locationReport.serverInfo}${locationReport.proofs.joinToString { "${it.prover}" }}"
            )

            Report.UserLocationReportResponse.newBuilder().apply {
                val messageNonce = generateNonce()
                nonce = Base64.getEncoder().encodeToString(messageNonce)

                ciphertext = symmetricCipher(
                    symKey,
                    Json.encodeToString(locationReport),
                    messageNonce
                )
            }.build()
        } else {
            Report.UserLocationReportResponse.getDefaultInstance()
        }
    }
}

class HA : HAGrpcKt.HACoroutineImplBase() {
    override suspend fun userLocationReport(request: Report.UserLocationReportRequest): Report.UserLocationReportResponse {
        val symKey: SecretKey = asymmetricDecipher(Database.key, request.key)
        val locationRequest: LocationRequest = requestToLocationRequest(symKey, request.nonce, request.ciphertext)
        val user = locationRequest.id
        val epoch = locationRequest.epoch
        val sig = locationRequest.signature

        val decipheredNonce = Base64.getDecoder().decode(request.nonce)

        val validNonce = try {
            Database.nonceDAO.storeHANonce(decipheredNonce)
        } catch (e: DataAccessException) {
            false
        }

        if (!validNonce || !RequestValidationService.validateHASignature(user, epoch, sig)) {
            return Report.UserLocationReportResponse.getDefaultInstance()
        }

        val locationReport = LocationReportService.getLocationReport(user, epoch, FLINE)
        return if (locationReport != null) {
            locationReport.signature = sign(
                Database.key,
                "${locationReport.id}${locationReport.epoch}${locationReport.coords}${locationReport.serverInfo}${locationReport.proofs.joinToString { "${it.prover}" }}"
            )

            Report.UserLocationReportResponse.newBuilder().apply {
                val messageNonce = generateNonce()
                nonce = Base64.getEncoder().encodeToString(messageNonce)

                ciphertext = symmetricCipher(
                    symKey,
                    Json.encodeToString(locationReport),
                    messageNonce
                )
            }.build()
        } else {
            Report.UserLocationReportResponse.getDefaultInstance()
        }
    }

    override suspend fun usersAtCoordinates(request: Report.UsersAtCoordinatesRequest): Report.UsersAtCoordinatesResponse {
        val symKey: SecretKey = asymmetricDecipher(Database.key, request.key)
        val usersRequest: CoordinatesRequest = requestToCoordinatesRequest(symKey, request.nonce, request.ciphertext)
        val epoch = usersRequest.epoch
        val coordinates = usersRequest.coords
        val signature = usersRequest.signature

        val decipheredNonce = Base64.getDecoder().decode(request.nonce)
        val validNonce = try {
            Database.nonceDAO.storeHANonce(decipheredNonce)
        } catch (e: DataAccessException) {
            false
        }

        if (!validNonce || !RequestValidationService.validateHASignature(epoch, coordinates, signature)) {
            return Report.UsersAtCoordinatesResponse.getDefaultInstance()
        }

        val users = LocationReportService.getUsersAtLocation(epoch, coordinates)

        return Report.UsersAtCoordinatesResponse.newBuilder().apply {
            val messageNonce = generateNonce()
            nonce = Base64.getEncoder().encodeToString(messageNonce)

            ciphertext = symmetricCipher(
                symKey,
                Json.encodeToString(
                    CoordinatesResponse(
                        users,
                        coordinates,
                        epoch,
                        sign(Database.key, "$coordinates$epoch${users.joinToString { "$it" }}")
                    )
                ), messageNonce
            )
        }.build()
    }
}

class ServerWrite : WriteGrpcKt.WriteCoroutineImplBase() {
    override suspend fun writeBroadcast(request: Server2Server.WriteBroadcastRequest): Server2Server.WriteBroadcastResponse {
        // TODO Check Report Content
        val report = LocationReport(-1, -1, Coordinates(-1, -1), "", mutableListOf())

        CommunicationService.deliverWrite(request.serverId, request.writtenTimestamp, report)

        return Server2Server.WriteBroadcastResponse.getDefaultInstance()
    }

    override suspend fun writeAcknowledgment(request: Server2Server.WriteAcknowledgmentRequest): Server2Server.WriteAcknowledgmentResponse {
        CommunicationService.deliverAcks(request.serverId, request.writtenTimestamp, request.acknowledgment)

        return Server2Server.WriteAcknowledgmentResponse.getDefaultInstance()
    }

    override suspend fun writeReturn(request: Server2Server.WriteReturnRequest): Server2Server.WriteReturnResponse {
        return super.writeReturn(request)
    }
}

class ServerRead : ReadGrpcKt.ReadCoroutineImplBase() {
    override suspend fun readBroadcast(request: Server2Server.ReadBroadcastRequest): Server2Server.ReadBroadcastResponse {
        CommunicationService.deliverRead(request.serverId, request.readId)

        return Server2Server.ReadBroadcastResponse.getDefaultInstance()
    }

    override suspend fun readAcknowledgment(request: Server2Server.ReadAcknowledgmentRequest): Server2Server.ReadAcknowledgmentResponse {
        // TODO Check Report Content
        val report = LocationReport(-1, -1, Coordinates(-1, -1), "", mutableListOf())

        CommunicationService.deliverValue(request.serverId, request.readId, request.maxTimeStamp, report)

        return Server2Server.ReadAcknowledgmentResponse.getDefaultInstance()
    }

    override suspend fun readReturn(request: Server2Server.ReadReturnRequest): Server2Server.ReadReturnResponse {
        return super.readReturn(request)
    }
}

fun requestToLocationReport(key: PrivateKey, nonce: String, encodedKey: String, ciphertext: String): LocationReport {
    val symKey: SecretKey = asymmetricDecipher(key, encodedKey)
    return Json.decodeFromString(symmetricDecipher(symKey, Base64.getDecoder().decode(nonce), ciphertext))
}

fun requestToLocationRequest(key: SecretKey, nonce: String, ciphertext: String): LocationRequest {
    return Json.decodeFromString(symmetricDecipher(key, Base64.getDecoder().decode(nonce), ciphertext))
}

fun requestToCoordinatesRequest(key: SecretKey, nonce: String, ciphertext: String): CoordinatesRequest {
    return Json.decodeFromString(symmetricDecipher(key, Base64.getDecoder().decode(nonce), ciphertext))
}


fun deriveKey(password: String): String {
    val spec: KeySpec = PBEKeySpec(password.toCharArray(), PASS_SALT.toByteArray(), PBKDF2_ITER, PBKDF2_KEY_SIZE)
    val factory: SecretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")

    return Base64.getEncoder().encodeToString(factory.generateSecret(spec).encoded)
}