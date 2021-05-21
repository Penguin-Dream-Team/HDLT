package sec.hdlt.server.services.grpc

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jooq.exception.DataAccessException
import sec.hdlt.protos.server.HAGrpcKt
import sec.hdlt.protos.server.Report
import sec.hdlt.server.*
import sec.hdlt.server.domain.*
import sec.hdlt.server.services.LocationReportService
import sec.hdlt.server.services.RequestValidationService
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey

class HAService(private val byzantineLevel: Int) : HAGrpcKt.HACoroutineImplBase() {
    override fun userLocationReport(requests: Flow<Report.UserLocationReportRequest>): Flow<Report.UserLocationReportResponse> {
        var symKey: SecretKey
        if (byzantineLevel >= 1 && Database.random.nextInt(100) < BYZ_PROB_NOT_SEND) {
            println("Dropping request")
            return flow { emit(Report.UserLocationReportResponse.getDefaultInstance()) }
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
                        Database.nonceDAO.storeHANonce(decipheredNonce)
                    } catch (e: DataAccessException) {
                        false
                    }

                    if (!validNonce || !RequestValidationService.validateHASignature(user, epoch, sig)) {
                        emit(Report.UserLocationReportResponse.newBuilder().apply {
                            val messageNonce = generateNonce()
                            nonce = Base64.getEncoder().encodeToString(messageNonce)

                            ciphertext = symmetricCipher(symKey, Json.encodeToString(INVALID_REQ), messageNonce)
                        }.build())
                        return@collect
                    }

                    println("[HA Request] received a report request for $user in epoch $epoch")

                    var channel: Channel<Unit>? = null
                    var locationReport: LocationResponse?

                    GET_REPORT_LISTENERS_LOCK.withLock {
                        locationReport = LocationReportService.getLocationReport(user, epoch, FLINE)

                        // Check if user has report
                        if (locationReport == null) {
                            // Send message stating report doesn't exist
                            emit(Report.UserLocationReportResponse.newBuilder().apply {
                                val messageNonce = generateNonce()
                                nonce = Base64.getEncoder().encodeToString(messageNonce)

                                ciphertext = symmetricCipher(symKey, Json.encodeToString(NO_REPORT), messageNonce)
                            }.build())

                        channel = Channel(Channel.CONFLATED)

                        // Add HA to listeners
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

                        locationReport = LocationReportService.getLocationReport(user, epoch, F)
                    }


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
