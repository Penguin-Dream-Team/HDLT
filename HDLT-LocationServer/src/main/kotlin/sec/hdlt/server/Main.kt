package sec.hdlt.server

import io.grpc.ServerBuilder
import kotlinx.coroutines.*
import sec.hdlt.protos.server.LocationGrpcKt
import sec.hdlt.protos.server.Report
import sec.hdlt.protos.server.Server
import sec.hdlt.protos.server.SetupGrpcKt
import sec.hdlt.server.data.Coordinates
import sec.hdlt.server.services.LocationReportService
import sec.hdlt.server.services.ReportValidationService
import java.io.IOException
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateException

const val KEY_ALIAS_PREFIX = "cert_hdlt_user_"
const val KEYSTORE_FILE = "/server.jks"
const val KEYSTORE_PASS = "KeyStoreServer"
var EPOCH_INTERVAL = 0L

fun main() {
    // Load the keystore
    val keyStore = KeyStore.getInstance("jks")
    val keystoreFile: InputStream = object {}.javaClass.getResourceAsStream(KEYSTORE_FILE)
    val reportsDirectory: String = System.getProperty("user.dir") + "/reports"
    val locationReportService = LocationReportService(reportsDirectory)
    val reportValidationService = ReportValidationService(keyStore)

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
        addService(Location(reportValidationService, locationReportService))
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
    private val locationReportService: LocationReportService
) : LocationGrpcKt.LocationCoroutineImplBase() {
    override suspend fun locationReport(request: Report.ReportRequest): Report.ReportResponse {
        val user1 = request.requesterId
        val user2 = request.proverId
        val epoch = request.epoch
        val coordinates1 = Coordinates(request.requesterLocation.x, request.requesterLocation.y)
        val coordinates2 = Coordinates(request.proverLocation.x, request.proverLocation.y)
        val sig1 = request.sig1
        val sig2 = request.sig2

        // FIXME: Check coordinates
        if (!reportValidationService.validateSignature(user1, user2, epoch, sig1, sig2)) {
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