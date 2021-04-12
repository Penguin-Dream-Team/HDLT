package sec.hdlt.server

import io.grpc.ServerBuilder
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import sec.hdlt.protos.server.LocationGrpcKt
import sec.hdlt.protos.server.Report
import sec.hdlt.protos.server.Server
import sec.hdlt.protos.server.SetupGrpcKt
import sec.hdlt.server.data.Coordinates
import sec.hdlt.server.data.LocationReport
import sec.hdlt.server.services.LocationReportService
import sec.hdlt.server.services.ReportValidationService
import java.io.IOException
import java.io.InputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

const val KEY_USER_PREFIX = "cert_hdlt_user_"
const val KEY_SERVER_ALIAS = "hdlt_server"
const val KEY_SERVER_PASS = "123"
const val KEYSTORE_FILE = "/server.jks"
const val KEYSTORE_PASS = "KeyStoreServer"
var EPOCH_INTERVAL = 0L

fun main() {
    // Load the keystore
    val keyStore = KeyStore.getInstance("jks")
    val keystoreFile: InputStream = object {}.javaClass.getResourceAsStream(KEYSTORE_FILE)
    val reportsDirectory: String = System.getProperty("user.dir") + "/reports/"
    val locationReportService = LocationReportService(reportsDirectory)
    val reportValidationService = ReportValidationService(keyStore)

    locationReportService.clearOldReports()
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

    val server = ServerBuilder.forPort(7777).apply {
        addService(Location(reportValidationService, locationReportService, serverKey))
        addService(Setup())
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
    private val reportValidationService: ReportValidationService,
    private val locationReportService: LocationReportService,
    private val key: PrivateKey,
) : LocationGrpcKt.LocationCoroutineImplBase() {
    override suspend fun locationReport(request: Report.ReportRequest): Report.ReportResponse {
        val report: LocationReport = requestToReport(key, request.nonce, request.key, request.ciphertext)
        val user = report.id
        val epoch = report.epoch
        val coordinates = report.location
        val sig = report.signature
        val proofs = report.proofs

        return if (!reportValidationService.validateSignature(user, epoch, sig) ||
            !reportValidationService.validateRequest(user, epoch, proofs)
        ) {
            Report.ReportResponse.newBuilder().apply {
                ack = false
            }.build()
        } else {
            locationReportService.storeLocationReport(epoch, user, coordinates, proofs)
            Report.ReportResponse.newBuilder().apply {
                ack = true
            }.build()
        }
    }

    override suspend fun userLocationReport(request: Report.UserLocationReportRequest): Report.UserLocationReportResponse {
        val user = request.user
        val epoch = request.epoch
        val sig = request.sig

        if (!reportValidationService.validateSignature(user, epoch, sig)) {
            return Report.UserLocationReportResponse.newBuilder().apply {
                this.user = user
                this.epoch = epoch
                this.location = null
            }.build()
        }

        val locationReport = locationReportService.getLocationReport(user.toLong(), epoch)
        return Report.UserLocationReportResponse.newBuilder().apply {
            if (locationReport != null) {
                val coordinates = Report.Coordinates.newBuilder().apply {
                    x = locationReport.coordinates.x
                    y = locationReport.coordinates.y
                }.build()

                this.user = locationReport.id
                this.epoch = locationReport.epoch
                this.location = coordinates
            }
        }.build()
    }
}

const val SYM_KEY_SIZE = 32

fun requestToReport(key: PrivateKey, nonce: String, encodedKey: String, ciphertext: String): LocationReport {
    val symKey: SecretKey = asymmetricDecipher(key, encodedKey)
    val decodedNonce: ByteArray = Base64.getDecoder().decode(nonce)
    val deciphered = symmetricDecipher(symKey, decodedNonce, ciphertext)
    return Json.decodeFromString(deciphered)
}

fun asymmetricDecipher(key: PrivateKey, ciphertext: String): SecretKey {
    val cipher: Cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
    cipher.init(Cipher.DECRYPT_MODE, key)

    val encodedKey = cipher.doFinal(Base64.getDecoder().decode(ciphertext))
    val decodedKey = Base64.getDecoder().decode(encodedKey)
    val secret = SecretKeySpec(decodedKey, 0, SYM_KEY_SIZE, "ChaCha20-Poly1305")
    return secret
}

fun symmetricDecipher(key: SecretKey, nonce: ByteArray, ciphertext: String): String {
    val cipher: Cipher = Cipher.getInstance("ChaCha20-Poly1305")
    val iv = IvParameterSpec(nonce)
    cipher.init(Cipher.DECRYPT_MODE, key, iv)

    return String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)))
}
