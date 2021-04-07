package sec.hdlt.user.service

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sec.hdlt.protos.master.*
import sec.hdlt.protos.server.*
import sec.hdlt.protos.user.*
import sec.hdlt.user.*
import sec.hdlt.user.domain.Board
import sec.hdlt.user.domain.Coordinates
import sec.hdlt.user.domain.EpochInfo
import sec.hdlt.user.domain.UserInfo
import java.security.Signature
import java.security.SignatureException
import java.util.*
import kotlin.random.Random

class MasterService(private val info: EpochInfo, private val serverChannel: ManagedChannel) :
    HDLTMasterGrpcKt.HDLTMasterCoroutineImplBase() {
    override suspend fun broadcastEpoch(request: Master.BroadcastEpochRequest): Master.BroadcastEpochResponse {
        println("Receiving epoch ${request.epoch}")
        info.epoch = request.epoch

        // FIXME: Store all boards
        info.board = Board()

        // Fill board
        request.cellsList.stream()
            .forEach { info.board.addUser(UserInfo(it.userId, Coordinates(it.x, it.y))) }

        info.position = info.board.getUser(info.id).coords

        GlobalScope.launch {
            val userInfo = info.clone()

            //delay(Random.nextLong(MIN_TIME_COM, MAX_TIME_COM) * 1000)

            communicate(userInfo, serverChannel)
        }

        return Master.BroadcastEpochResponse.newBuilder().apply {
            userId = info.id
            ok = true
        }.build()
    }
}

suspend fun communicate(info: EpochInfo, serverChannel: ManagedChannel) {
    // Byzantine Level 5: Skip communication
    if (info.byzantineLevel >= 5 && Random.nextInt(100) < BYZ_PROB_SKIP_COM) {
        return
    }

    var coords = info.position

    var request: User.LocationProofRequest = User.LocationProofRequest.getDefaultInstance()

    val fakeAll: Boolean = info.byzantineLevel >= 4 && Random.nextInt(100) < BYZ_PROB_ALL_LOC

    // Byzantine Level 4: Fake location, near every user
    val users: List<UserInfo> = if (fakeAll) {
        println("Faking location, near every user")

        info.board.getAllUsers()
    } else {
        // Byzantine Level 7: Fake location for this epoch
        if (info.byzantineLevel >= 7 && Random.nextInt(100) < BYZ_PROB_FAKE_LOC) {
            println("Faking location of this epoch")
            coords = Coordinates(Random.nextInt(100), Random.nextInt(100))
        }

        // Create request once (will be equal for all gRPC calls)
        request = User.LocationProofRequest.newBuilder().apply {
            id = info.id
            epoch = info.epoch
            signature = try {
                val sig: Signature = Signature.getInstance("SHA256withECDSA")
                sig.initSign(info.key)
                sig.update("${info.id}${info.epoch}".toByteArray())
                Base64.getEncoder().encodeToString(sig.sign())
            } catch (e: SignatureException) {
                println("Couldn't sign message")
                return
            }
        }.build()

        info.board.getNearUsers(info.id, info.position)
    }

    // Launch call for each user
    for (user: UserInfo in users) {
        coroutineScope {
            val userStub =
                LocationProofGrpcKt.LocationProofCoroutineStub(
                    ManagedChannelBuilder.forAddress("localhost", user.port).usePlaintext().build()
                )

            // Byzantine Level 4: Fake location, near every user
            if (fakeAll) {
                request = User.LocationProofRequest.newBuilder().apply {
                    id = info.id
                    epoch = info.epoch
                    signature = try {
                        val sig: Signature = Signature.getInstance("SHA256withECDSA")
                        sig.initSign(info.key)
                        sig.update("${info.id}${info.epoch}".toByteArray())
                        Base64.getEncoder().encodeToString(sig.sign())
                    } catch (e: SignatureException) {
                        println("Couldn't sign message")
                        return@coroutineScope
                    }
                }.build()
            }

            val response: User.LocationProofResponse

            try {
                response = userStub.requestLocationProof(request)
            } catch (e: StatusRuntimeException) {
                when (e.status.code) {
                    Status.CANCELLED.code -> {
                        println("Responder detected invalid signature")
                    }
                    Status.FAILED_PRECONDITION.code -> {
                        println("Responder thinks it is far")
                    }
                    Status.UNAUTHENTICATED.code -> {
                        println("Responder couldn't deliver message")
                    }
                    Status.INVALID_ARGUMENT.code -> {
                        println("Responder is in different epoch")
                    }
                    else -> println("Unknown error")
                }

                return@coroutineScope
            } catch (e: Exception) {
                e.printStackTrace()
                return@coroutineScope
            }

            // Byzantine Level 8: No verification of data
            if (info.byzantineLevel >= 8 && Random.nextInt(100) < BYZ_PROB_NO_VER) {
                // Skip verification
            } else {
                // Check response
                if (user.id != response.proverId || info.id != response.requesterId) {
                    println("User ids do not match")
                    return@coroutineScope
                } else if (info.epoch != response.epoch) {
                    println("User is in different epoch")
                    return@coroutineScope
                }
            }

            // Check signature
            try {
                val sig: Signature = Signature.getInstance("SHA256withECDSA")
                sig.initVerify(info.keyStore.getCertificate(KEY_ALIAS_PREFIX + user.id))
                sig.update("${info.id}${user.id}${info.epoch}".toByteArray())
                sig.verify(Base64.getDecoder().decode(response.signature))
            } catch (e: SignatureException) {
                println("Invalid signature detected")
                return@coroutineScope
            } catch (e: IllegalArgumentException) {
                println("Invalid base64 detected")
                return@coroutineScope
            }

            // Byzantine Level 1: No communication with server
            if (info.byzantineLevel >= 1 && Random.nextInt(100) < BYZ_PROB_NOT_SEND) {
                println("Not communicating location proof to server")
                return@coroutineScope
            }

            val serverRequest: Report.ReportRequest

            // Byzantine Level 2: Tamper with fields
            if (info.byzantineLevel >= 2 && Random.nextInt(100) < BYZ_PROB_TAMPER) {
                println("Tampering with request to server fields")
                var tampered = false
                serverRequest = Report.ReportRequest.newBuilder().apply {
                    proverId = if (!tampered && Random.nextBoolean()) {
                        tampered = true; Random.nextInt(BYZ_MAX_ID_TAMPER)
                    } else {
                        info.id
                    }
                    requesterId = if (!tampered && Random.nextBoolean()) {
                        tampered = true; Random.nextInt(BYZ_MAX_ID_TAMPER)
                    } else {
                        response.proverId
                    }
                    epoch = if (!tampered && Random.nextBoolean()) {
                        tampered = true; Random.nextInt(BYZ_MAX_EP_TAMPER)
                    } else {
                        info.epoch
                    }
                    proverLocation = Report.Coordinates.newBuilder().apply {
                        x = if (!tampered && Random.nextBoolean()) {
                            tampered = true; Random.nextInt(BYZ_MAX_COORDS_TAMPER)
                        } else {
                            coords.x
                        }
                        y = if (tampered && Random.nextBoolean()) {
                            tampered = true; Random.nextInt(BYZ_MAX_COORDS_TAMPER)
                        } else {
                            coords.y
                        }
                    }.build()

                    requesterLocation = Report.Coordinates.newBuilder().apply {
                        x = if (!tampered && Random.nextBoolean()) {
                            tampered = true; Random.nextInt(BYZ_MAX_COORDS_TAMPER)
                        } else {
                            user.coords.x
                        }
                        y = if (tampered && Random.nextBoolean()) {
                            tampered = true; Random.nextInt(BYZ_MAX_COORDS_TAMPER)
                        } else {
                            user.coords.y
                        }
                    }.build()

                    sig1 = if (!tampered && Random.nextBoolean()) {
                        tampered = true; Base64.getEncoder().encodeToString(
                            Random.nextBytes(
                                BYZ_BYTES_TAMPER
                            )
                        )
                    } else {
                        try {
                            val sig: Signature = Signature.getInstance("SHA256withECDSA")
                            sig.initSign(info.key)
                            sig.update("${info.id}${user.id}${info.epoch}".toByteArray())
                            Base64.getEncoder().encodeToString(sig.sign())
                        } catch (e: SignatureException) {
                            println("Couldn't sign message")
                            return@coroutineScope
                        }
                    }

                    sig2 = if (!tampered && Random.nextBoolean()) {
                        tampered = true; Base64.getEncoder().encodeToString(
                            Random.nextBytes(
                                BYZ_BYTES_TAMPER
                            )
                        )
                    } else {
                        response.signature
                    }
                }.build()
            } else {
                // Non-byzantine request
                serverRequest = Report.ReportRequest.newBuilder().apply {
                    requesterId = info.id
                    proverId = response.proverId

                    epoch = info.epoch

                    requesterLocation = Report.Coordinates.newBuilder().apply {
                        x = coords.x
                        y = coords.y
                    }.build()

                    proverLocation = Report.Coordinates.newBuilder().apply {
                        x = user.coords.x
                        y = user.coords.y
                    }.build()

                    sig1 = try {
                        val sig: Signature = Signature.getInstance("SHA256withECDSA")
                        sig.initSign(info.key)
                        sig.update("${info.id}${user.id}${info.epoch}".toByteArray())
                        Base64.getEncoder().encodeToString(sig.sign())
                    } catch (e: SignatureException) {
                        println("Couldn't sign message")
                        return@coroutineScope
                    }

                    sig2 = response.signature
                }.build()
            }

            // Send request to server
            val serverStub = LocationGrpcKt.LocationCoroutineStub(serverChannel)

            if (serverStub.locationReport(serverRequest).ack) {
                println("Request was OK")
            } else {
                println("BUSTED")
            }
        }
    }

    // Byzantine Level 0: Create non-existent request
    if (info.byzantineLevel >= 0 && Random.nextInt(100) < BYZ_PROB_DUMB) {
        println("Forging location proof")

        val user = info.board.getRandomUser()

        val serverStub = LocationGrpcKt.LocationCoroutineStub(serverChannel)
        if (serverStub.locationReport(Report.ReportRequest.newBuilder().apply {
                requesterId = user.id
                proverId = info.id

                epoch = info.epoch

                requesterLocation = Report.Coordinates.newBuilder().apply {
                    x = user.coords.x
                    y = user.coords.y
                }.build()

                proverLocation = Report.Coordinates.newBuilder().apply {
                    x = info.position.x
                    y = info.position.y
                }.build()

                sig1 = try {
                    val sig: Signature = Signature.getInstance("SHA256withECDSA")
                    sig.initSign(info.key)
                    sig.update("${user.id}${info.id}${info.epoch}".toByteArray())
                    Base64.getEncoder().encodeToString(sig.sign())
                } catch (e: SignatureException) {
                    println("Couldn't sign message")
                    return
                }

                sig2 = sig1

            }.build()).ack) {
            println("Request was OK :O")
        } else {
            println("Dumb user busted")
        }
    }
}