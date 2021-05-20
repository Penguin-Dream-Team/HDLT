package sec.hdlt.server.services.grpc

import io.grpc.Status
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jooq.exception.DataAccessException
import sec.hdlt.protos.server.LocationGrpcKt
import sec.hdlt.protos.server.Report
import sec.hdlt.server.*
import sec.hdlt.server.domain.Database
import sec.hdlt.server.domain.LocationReport
import sec.hdlt.server.domain.LocationRequest
import sec.hdlt.server.domain.LocationResponse
import sec.hdlt.server.services.LocationReportService
import sec.hdlt.server.services.RequestValidationService
import java.lang.invoke.MethodHandles
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey

class LocationService(val byzantineLevel: Int) : LocationGrpcKt.LocationCoroutineImplBase() {
    override suspend fun submitLocationReport(request: Report.ReportRequest): Report.ReportResponse {

        // Byzantine Level 1: Ignore request
        if (byzantineLevel >= 1 && Database.random.nextInt(100) < BYZ_PROB_NOT_SEND) {
            println("Dropping request")
            return Report.ReportResponse.getDefaultInstance()
        }


        try {
            val symKey: SecretKey = asymmetricDecipher(Database.key, request.key)
            val report: LocationReport = requestToLocationReport(symKey, request.nonce, request.ciphertext)

            val user = report.id
            val epoch = report.epoch
            val coordinates = report.location
            val sig = report.signature
            var proofs = report.proofs

            val decipheredNonce = Base64.getDecoder().decode(request.nonce)
            val validNonce = try {
                Database.nonceDAO.storeUserNonce(decipheredNonce, user)
            } catch (e: DataAccessException) {
                false
            }

            if (validNonce && RequestValidationService.validateSignature(
                    user,
                    epoch,
                    coordinates,
                    sig
                ) && !LocationReportService.hasReport(user, epoch)
            ) {
                proofs = RequestValidationService.getValidProofs(user, epoch, proofs)

                if (proofs.isNotEmpty()) {
                    println("[EPOCH ${report.epoch}] received a write request from user $user")

                    val ack: Boolean

                    // Check if writer is byzantine with broadcast
                    if (broadcast(report)) {
                        GET_REPORT_LISTENERS_LOCK.withLock {
                            ack = LocationReportService.storeLocationReport(report, epoch, user, coordinates, proofs)

                            // Check if valid report and if there are listeners
                            if (ack && GET_REPORT_LISTENERS.containsKey(epoch)) {
                                val epochListeners = GET_REPORT_LISTENERS[epoch]!!
                                if (epochListeners.containsKey(user)) {
                                    epochListeners[user]!!.stream().forEach {
                                        it.offer(Unit)
                                    }

                                    epochListeners.remove(user)
                                }
                            }
                        }

                        return Report.ReportResponse.newBuilder().apply {
                            val messageNonce = generateNonce()
                            nonce = Base64.getEncoder().encodeToString(messageNonce)

                            ciphertext = symmetricCipher(
                                symKey,
                                Json.encodeToString(ack),
                                messageNonce
                            )
                        }.build()
                    }
                }
            }

        } catch (e: Exception) {
            // TODO: DEBUG
            println("SUBMIT ERROR")
            e.printStackTrace()
        }
        throw Status.INVALID_ARGUMENT.asException()
    }
}