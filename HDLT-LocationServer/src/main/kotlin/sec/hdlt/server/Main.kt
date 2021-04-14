package sec.hdlt.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.grpc.ServerBuilder
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jooq.SQLDialect
import org.jooq.impl.DefaultConfiguration
import sec.hdlt.protos.server.*
import sec.hdlt.server.dao.ReportDAO
import sec.hdlt.server.data.*
import sec.hdlt.server.services.LocationReportService
import sec.hdlt.server.services.ReportValidationService
import java.io.IOException
import java.io.InputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey

const val KEY_USER_PREFIX = "cert_hdlt_user_"
const val KEY_HA_ALIAS = "hdlt_ha"
const val KEY_SERVER_ALIAS = "hdlt_server"
const val KEY_SERVER_PASS = "123"
const val KEYSTORE_FILE = "/server.jks"
const val KEYSTORE_PASS = "KeyStoreServer"
var EPOCH_INTERVAL = 0L

fun initDatabaseDaos(): ReportDAO {
    val dbConfig = DefaultConfiguration()
        .set(SQLDialect.SQLITE)
        .set(HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:src/main/resources/db/database.sqlite"
            maximumPoolSize = 15
        }))
    return ReportDAO(dbConfig)
}

fun main() {
    // Load the keystore
    val keyStore = KeyStore.getInstance("jks")
    val keystoreFile: InputStream = object {}.javaClass.getResourceAsStream(KEYSTORE_FILE)!!

    val reportDao = initDatabaseDaos()

    try {
        keyStore.load(keystoreFile, KEYSTORE_PASS.toCharArray())
    } catch (e: IOException) {
        println("Couldn't open KeyStore file")
        return
    } catch (e: CertificateException) {
        println("Couldn't load all keys/certificates")
        return
    }

    val serverKey: PrivateKey = keyStore.getKey(KEY_SERVER_ALIAS, KEY_SERVER_PASS.toCharArray()) as PrivateKey

    Database(keyStore, serverKey, reportDao)

    val server = ServerBuilder.forPort(7777).apply {
        addService(Location(ConcurrentHashMap.newKeySet()))
        addService(Setup())
        addService(HA(ConcurrentHashMap.newKeySet()))
    }.build()

    server.start()
    server.awaitTermination()
}

class Setup : SetupGrpcKt.SetupCoroutineImplBase() {
    override suspend fun broadcastEpoch(request: Server.BroadcastEpochRequest): Server.BroadcastEpochResponse {
        EPOCH_INTERVAL = request.epoch.toLong()

        return Server.BroadcastEpochResponse.newBuilder().apply {
            ok = true
        }.build()
    }
}

class Location(
    private val usedNonces: MutableSet<ByteArray>
) : LocationGrpcKt.LocationCoroutineImplBase() {
    override suspend fun locationReport(request: Report.ReportRequest): Report.ReportResponse {
        val report: LocationReport = requestToLocationReport(Database.key, request.nonce, request.key, request.ciphertext)
        val user = report.id
        val epoch = report.epoch
        val coordinates = report.location
        val sig = report.signature
        val proofs = report.proofs

        return if (!ReportValidationService.validateSignature(user, epoch, coordinates, sig) ||
            !ReportValidationService.validateRequest(user, epoch, proofs)
        ) {
            Report.ReportResponse.newBuilder().apply {
                ack = false
            }.build()
        } else {
            LocationReportService.storeLocationReport(epoch, user, coordinates, proofs)
            Report.ReportResponse.newBuilder().apply {
                ack = true
            }.build()
        }
    }

    override suspend fun userLocationReport(request: Report.UserLocationReportRequest): Report.UserLocationReportResponse {
        val symKey: SecretKey = asymmetricDecipher(Database.key, request.key)
        val locationRequest: LocationRequest = requestToLocationRequest(symKey, request.nonce, request.ciphertext)
        val user = locationRequest.id
        val epoch = locationRequest.epoch
        val sig = locationRequest.signature

        val decipheredNonce = Base64.getDecoder().decode(request.nonce)

        if (!ReportValidationService.validateSignature(user, epoch, sig) || usedNonces.contains(decipheredNonce)) {
            return Report.UserLocationReportResponse.getDefaultInstance()
        }

        usedNonces.add(decipheredNonce)
        val locationReport = LocationReportService.getLocationReport(user, epoch)
        return if (locationReport != null) {
            Report.UserLocationReportResponse.newBuilder().apply {
                val messageNonce = generateNonce()
                nonce = Base64.getEncoder().encodeToString(messageNonce)

                ciphertext = symmetricCipher(
                    symKey,
                    Json.encodeToString(
                        LocationResponse(
                            locationReport.id,
                            locationReport.epoch,
                            locationReport.coordinates,
                            sign(Database.key, "${locationReport.id}${locationReport.epoch}${locationReport.coordinates}")
                        )
                    ),
                    messageNonce
                )
            }.build()
        } else {
            Report.UserLocationReportResponse.getDefaultInstance()
        }
    }
}

class HA(val usedNonces: MutableSet<ByteArray>) : HAGrpcKt.HACoroutineImplBase() {
    override suspend fun userLocationReport(request: Report.UserLocationReportRequest): Report.UserLocationReportResponse {
        val symKey: SecretKey = asymmetricDecipher(Database.key, request.key)
        val locationRequest: LocationRequest = requestToLocationRequest(symKey, request.nonce, request.ciphertext)
        val user = locationRequest.id
        val epoch = locationRequest.epoch
        val sig = locationRequest.signature

        val decipheredNonce = Base64.getDecoder().decode(request.nonce)

        if (!ReportValidationService.validateHASignature(user, epoch, sig) || usedNonces.contains(decipheredNonce)) {
            return Report.UserLocationReportResponse.getDefaultInstance()
        }

        usedNonces.add(decipheredNonce)
        val locationReport = LocationReportService.getLocationReport(user, epoch)
        return if (locationReport != null) {
            Report.UserLocationReportResponse.newBuilder().apply {
                val messageNonce = generateNonce()
                nonce = Base64.getEncoder().encodeToString(messageNonce)

                ciphertext = symmetricCipher(
                    symKey,
                    Json.encodeToString(
                        LocationResponse(
                            locationReport.id,
                            locationReport.epoch,
                            locationReport.coordinates,
                            sign(
                                Database.key,
                                "${locationReport.id}${locationReport.epoch}${locationReport.coordinates}"
                            )
                        )
                    ),
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

        val decipheredNonce = Base64.getDecoder().decode(request.nonce)

        return Report.UsersAtCoordinatesResponse.getDefaultInstance()
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