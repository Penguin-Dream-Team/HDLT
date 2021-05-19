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
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey

class LocationService : LocationGrpcKt.LocationCoroutineImplBase() {
    override suspend fun submitLocationReport(request: Report.ReportRequest): Report.ReportResponse {
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
                // TODO: Broadcast

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

        throw Status.INVALID_ARGUMENT.asException()
    }

    override fun getLocationReport(requests: Flow<Report.UserLocationReportRequest>): Flow<Report.UserLocationReportResponse> {
        var symKey: SecretKey

        return flow {
            requests.collect { request ->
                run {
                    // Get secret key
                    symKey = asymmetricDecipher(Database.key, request.key)

                    val deciphered = decipherRequest(symKey, request.nonce, request.ciphertext)

                    // Check if end of communication
                    if (deciphered == END_COMM) {
                        emit(Report.UserLocationReportResponse.newBuilder().apply {
                            val messageNonce = generateNonce()
                            nonce = Base64.getEncoder().encodeToString(messageNonce)

                            ciphertext = symmetricCipher(symKey, Json.encodeToString(END_COMM), messageNonce)
                        }.build())
                        return@collect
                    }

                    val locationRequest: LocationRequest = Json.decodeFromString(deciphered)

                    val user = locationRequest.id
                    val epoch = locationRequest.epoch
                    val sig = locationRequest.signature

                    val decipheredNonce = Base64.getDecoder().decode(request.nonce)
                    val validNonce = try {
                        Database.nonceDAO.storeUserNonce(decipheredNonce, user)
                    } catch (e: DataAccessException) {
                        false
                    }

                    // Check if invalid request
                    if (!validNonce || !RequestValidationService.validateSignature(user, epoch, sig)) {
                        emit(Report.UserLocationReportResponse.newBuilder().apply {
                            val messageNonce = generateNonce()
                            nonce = Base64.getEncoder().encodeToString(messageNonce)

                            ciphertext = symmetricCipher(symKey, Json.encodeToString(INVALID_REQ), messageNonce)
                        }.build())
                        return@collect
                    }

                    println("[EPOCH $epoch] received a read request from user $user")

                    var channel: Channel<Unit>? = null
                    var locationReport: LocationResponse?
                    GET_REPORT_LISTENERS_LOCK.withLock {
                         locationReport = LocationReportService.getLocationReport(user, epoch, F)

                        // Check if user has report
                        if (locationReport == null) {
                            // Send message stating report doesn't exist
                            emit(Report.UserLocationReportResponse.newBuilder().apply {
                                val messageNonce = generateNonce()
                                nonce = Base64.getEncoder().encodeToString(messageNonce)

                                ciphertext = symmetricCipher(symKey, Json.encodeToString(NO_REPORT), messageNonce)
                            }.build())

                            channel = Channel(Channel.CONFLATED)

                            // Add user to listeners
                            val epochListeners: ConcurrentHashMap<Int, MutableList<Channel<Unit>>>
                            if (GET_REPORT_LISTENERS.containsKey(epoch)) {
                                epochListeners = GET_REPORT_LISTENERS[epoch]!!
                            } else {
                                epochListeners = ConcurrentHashMap<Int, MutableList<Channel<Unit>>>()
                                GET_REPORT_LISTENERS[epoch] = epochListeners
                            }

                            if (epochListeners.containsKey(user)) {
                                epochListeners[user]!!.add(channel!!)
                            } else {
                                epochListeners[user] = mutableListOf(channel!!)
                            }
                        }
                    }

                    if (channel != null) {
                        // Wait for write to wake up
                        channel!!.receive()
                    }

                    locationReport = LocationReportService.getLocationReport(user, epoch, F)

                    // Sign report
                    locationReport!!.signature = sign(
                        Database.key,
                        "${locationReport!!.id}${locationReport!!.epoch}${locationReport!!.coords}${locationReport!!.serverInfo}${locationReport!!.proofs.joinToString { "${it.prover}" }}"
                    )

                    // Send report
                    emit(Report.UserLocationReportResponse.newBuilder().apply {
                        val messageNonce = generateNonce()
                        nonce = Base64.getEncoder().encodeToString(messageNonce)

                        ciphertext = symmetricCipher(symKey, Json.encodeToString(locationReport), messageNonce)
                    }.build())
                }
            }
        }
    }
}