package sec.hdlt.server

import io.grpc.ServerBuilder
import sec.hdlt.protos.server.LocationGrpcKt
import sec.hdlt.protos.server.Report
import sec.hdlt.server.data.Coordinates
import sec.hdlt.server.services.LocationReportService
import sec.hdlt.server.services.ReportValidationService
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateException

const val KEY_ALIAS_PREFIX = "cert_hdlt_user_"
const val KEYSTORE_FILE = "/server.jks"
const val KEYSTORE_PASS = "KeyStoreServer"

fun main() {
    // Load the keystore
    val keyStore = KeyStore.getInstance("jks")
    val keystoreFile: InputStream = object {}.javaClass.getResourceAsStream(KEYSTORE_FILE)

    try {
        keyStore.load(keystoreFile, KEYSTORE_PASS.toCharArray())
    } catch(e: IOException) {
        println("Couldn't open KeyStore file")
        return
    } catch(e: CertificateException) {
        println("Couldn't load all keys/certificates")
        return
    }

    val server = ServerBuilder.forPort(7777).apply {
        addService(Location(keyStore))
    }.build()

    server.start()
    server.awaitTermination()
}

class Location(keystore: KeyStore) : LocationGrpcKt.LocationCoroutineImplBase() {
    private val reportsDirectory: String = System.getProperty("user.dir") + "/reports"
    private val locationReportService = LocationReportService(reportsDirectory)
    private val reportValidationService = ReportValidationService(keystore)

    override suspend fun locationReport(request: Report.ReportRequest): Report.ReportResponse {
        val user1 = request.requesterId
        val user2 = request.proverId
        val epoch = request.epoch
        val coordinates1 = Coordinates(request.requesterLocation.x, request.requesterLocation.y)
        val coordinates2 = Coordinates(request.proverLocation.x, request.proverLocation.y)
        val sig1 = request.sig1
        val sig2 = request.sig2

        // FIXME: Check signatures, ids and coordinates
        if (!reportValidationService.validateSignature(user1, user2, epoch, coordinates1, coordinates2, sig1, sig2)) {
            return Report.ReportResponse.newBuilder().apply {
                ack = false
            }.build()
        }

        if (!reportValidationService.validateRequest(user1, user2, coordinates1, coordinates2)) {
            return Report.ReportResponse.newBuilder().apply {
                ack = false
            }.build()
        }

        locationReportService.storeLocationReport(epoch, user1, user2, coordinates1, coordinates2)

        return Report.ReportResponse.newBuilder().apply {
            ack = true
        }.build()
    }

    override suspend fun userLocationReport(request: Report.UserLocationReportRequest): Report.UserLocationReportResponse {
        val user = request.user
        val epoch = request.epoch
        val sig = request.sig

        // FIXME: Check signature and id
        if (!reportValidationService.validateSignature(user, epoch, sig)) {
            return Report.UserLocationReportResponse.newBuilder().apply {
                this.user = user
                this.epoch = epoch
                this.location = null
            }.build()
        }

        val locationReport = locationReportService.getLocationReport(user, epoch)
        return Report.UserLocationReportResponse.newBuilder().apply {
            if (locationReport != null) {
                val coordinates = Report.Coordinates.newBuilder().apply {
                    x = locationReport.coordinates.x
                    y = locationReport.coordinates.y
                }.build()

                this.user = locationReport.user
                this.epoch = locationReport.epoch
                this.location = coordinates
            }
        }.build()
    }
}