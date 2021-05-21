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
import sec.hdlt.server.domain.*
import sec.hdlt.server.services.LocationReportService
import sec.hdlt.server.services.RequestValidationService
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
        val symKey: SecretKey = asymmetricDecipher(Database.key, request.key)
        var report: LocationReport = requestToLocationReport(symKey, request.nonce, request.ciphertext)

        val user = report.id
        val epoch = report.epoch
        val coordinates = report.location
        val sig = report.signature
        var proofs = report.proofs

        val decipheredNonce = Base64.getDecoder().decode(request.nonce)
        val validNonce = try {
            Database.nonceDAO.storeUserNonce(decipheredNonce, user)
        } catch (e: DataAccessException) {
            e.printStackTrace()
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
                val broadcastValue = broadcast(report)
                if (broadcastValue.isPresent) {
                    report = broadcastValue.get()
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
        throw Status.INVALID_ARGUMENT.asException()
    }

    override fun getLocationReport(requests: Flow<Report.UserLocationReportRequest>): Flow<Report.UserLocationReportResponse> {
        var symKey: SecretKey

        // Byzantine Level 1: Ignore request
        if (byzantineLevel >= 1 && Database.random.nextInt(100) < BYZ_PROB_NOT_SEND) {
            println("Dropping request")
            return flow { requests.collect() }
        }

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

    override suspend fun getWitnessProofs(request: Report.WitnessProofsRequest): Report.WitnessProofsResponse {
        try {
            val symKey: SecretKey = asymmetricDecipher(Database.key, request.key)
            val witnessRequest: WitnessRequest = requestToWitnessRequest(symKey, request.nonce, request.ciphertext)

            val user = witnessRequest.userId
            val epochs = witnessRequest.epochs
            val signature = witnessRequest.signature

            val decipheredNonce = Base64.getDecoder().decode(request.nonce)
            val validNonce = try {
                Database.nonceDAO.storeUserNonce(decipheredNonce, user)
            } catch (e: DataAccessException) {
                false
            }

            if (validNonce && RequestValidationService.validateSignature(
                    user,
                    epochs,
                    signature
                )
            ) {
                val witnessProofs = LocationReportService.getWitnessProofs(user, epochs)

                if (witnessProofs.isNotEmpty()) {
                    println("$user request his proofs as Witness")

                    val ack: Boolean

                    // TODO Read Regular
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