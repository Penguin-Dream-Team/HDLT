package sec.hdlt.server.services.grpc

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jooq.exception.DataAccessException
import sec.hdlt.protos.server.HAGrpcKt
import sec.hdlt.protos.server.Report
import sec.hdlt.server.*
import sec.hdlt.server.domain.CoordinatesRequest
import sec.hdlt.server.domain.CoordinatesResponse
import sec.hdlt.server.domain.Database
import sec.hdlt.server.domain.LocationRequest
import sec.hdlt.server.services.LocationReportService
import sec.hdlt.server.services.RequestValidationService
import java.util.*
import javax.crypto.SecretKey

class HAService(val byzantineLevel: Int) : HAGrpcKt.HACoroutineImplBase() {
    override suspend fun userLocationReport(request: Report.UserLocationReportRequest): Report.UserLocationReportResponse {
        // Byzantine Level 1: Ignore request
        if (byzantineLevel >= 1 && Database.random.nextInt(100) < BYZ_PROB_NOT_SEND) {
            println("Dropping request")
            return Report.UserLocationReportResponse.getDefaultInstance()
        }

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
        // Byzantine Level 1: Ignore request
        if (byzantineLevel >= 1 && Database.random.nextInt(100) < BYZ_PROB_NOT_SEND) {
            println("Dropping request")
            return Report.UsersAtCoordinatesResponse.getDefaultInstance()
        }

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
