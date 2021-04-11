package sec.hdlt.user.service

import io.grpc.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sec.hdlt.protos.master.*
import sec.hdlt.protos.server.*
import sec.hdlt.protos.user.*
import sec.hdlt.user.*
import sec.hdlt.user.domain.*
import java.security.Signature
import java.security.SignatureException
import java.util.*
import kotlin.random.Random
import kotlin.streams.toList

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

            // FIXME: activate this
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
    // Byzantine Level 1: Skip communication
    if (info.byzantineLevel >= 1 && Random.nextInt(100) < BYZ_PROB_NOT_SEND) {
        return
    }

    val users: List<UserInfo> = info.board.getAllUsers()

    // Byzantine Level 2: Tamper with fields
    // Create request once (will be equal for all gRPC calls)
    val request = User.LocationProofRequest.newBuilder().apply {
        id = if(info.byzantineLevel >= 2 && Random.nextInt(100) < BYZ_PROB_TAMPER) {
            Random.nextInt(BYZ_MAX_ID_TAMPER)
        } else {
            info.id
        }
        epoch = if (info.byzantineLevel >= 2 && Random.nextInt(100) < BYZ_PROB_TAMPER) {
            Random.nextInt(BYZ_MAX_EP_TAMPER)
        } else {
            info.epoch
        }

        signature = if (info.byzantineLevel >= 2 && Random.nextInt(100) < BYZ_PROB_TAMPER) {
           Base64.getEncoder().encodeToString(Random.nextBytes(BYZ_BYTES_TAMPER))
        } else {
            try {
                val sig: Signature = Signature.getInstance("SHA256withECDSA")
                sig.initSign(info.key)
                sig.update("${info.id}${info.epoch}".toByteArray())
                Base64.getEncoder().encodeToString(sig.sign())
            } catch (e: SignatureException) {
                println("Couldn't sign message")
                return
            }
        }
    }.build()

    val mutex = Mutex()
    val responses = mutableListOf<Proof>()

    // Launch call for each user
    coroutineScope {
        for (user: UserInfo in users) {
            launch {
                val userChannel: ManagedChannel =
                    ManagedChannelBuilder.forAddress("localhost", user.port).usePlaintext().build()
                val userStub = LocationProofGrpcKt.LocationProofCoroutineStub(userChannel)

                val response: User.LocationProofResponse

                try {
                    response = userStub.requestLocationProof(request)
                    userChannel.shutdownNow()
                } catch (e: StatusRuntimeException) {
                    when (e.status.code) {
                        Status.CANCELLED.code -> {
                            println("Responder detected invalid signature")
                        }
                        Status.FAILED_PRECONDITION.code -> {
                            //println("Responder thinks it is far")
                        }
                        Status.UNAUTHENTICATED.code -> {
                            println("Responder couldn't deliver message")
                        }
                        Status.INVALID_ARGUMENT.code -> {
                            println("Responder is in different epoch")
                        }
                        else -> println("Unknown error")
                    }

                    userChannel.shutdownNow()
                    return@launch
                } catch (e: StatusException) {
                    when (e.status.code) {
                        Status.CANCELLED.code -> {
                            println("Responder detected invalid signature")
                        }
                        Status.FAILED_PRECONDITION.code -> {
                            //println("Responder thinks it is far")
                        }
                        Status.UNAUTHENTICATED.code -> {
                            println("Responder couldn't deliver message")
                        }
                        Status.INVALID_ARGUMENT.code -> {
                            println("Responder is in different epoch")
                        }
                        else -> println("Unknown error")
                    }

                    userChannel.shutdownNow()
                    return@launch
                } catch (e: Exception) {
                    e.printStackTrace()
                    userChannel.shutdownNow()
                    return@launch
                }

                // Byzantine Level 5: No verification of data
                if (info.byzantineLevel >= 5 && Random.nextInt(100) < BYZ_PROB_NO_VER) {
                    // Skip verifications
                } else {
                    // Check response
                    if (response.epoch == 0 && response.proverId == 0 && response.requesterId == 0 && response.signature.equals(
                            ""
                        )
                    ) {
                        return@launch
                    } else if (user.id != response.proverId || info.id != response.requesterId) {
                        println("User ids do not match")
                        return@launch
                    } else if (info.epoch != response.epoch) {
                        println("User is in different epoch")
                        return@launch
                    } else if (!info.position.isNear(info.board.getUserCoords(user.id))) {
                        println("User is far")
                        return@launch
                    }

                    // Check signature
                    try {
                        val sig: Signature = Signature.getInstance("SHA256withECDSA")
                        sig.initVerify(info.keyStore.getCertificate(KEY_ALIAS_PREFIX + user.id))
                        sig.update("${info.id}${user.id}${info.epoch}".toByteArray())
                        if (!sig.verify(Base64.getDecoder().decode(response.signature))) {
                            println("Invalid signature detected")
                            userChannel.shutdownNow()
                            return@launch
                        }
                    } catch (e: SignatureException) {
                        println("Invalid signature detected")
                        return@launch
                    } catch (e: IllegalArgumentException) {
                        println("Invalid base64 detected")
                        return@launch
                    }

                    mutex.withLock {
                        responses.add(
                            Proof(
                                response.requesterId,
                                response.proverId,
                                response.epoch,
                                response.signature
                            )
                        )
                    }
                }
            } // launch coroutine
        } // for loop
    } // Coroutine scope

    // Byzantine Level 2: Tamper with fields
    val serverRequest: Report.ReportRequest = if (info.byzantineLevel >= 2 && Random.nextInt(100) < BYZ_PROB_TAMPER) {
        println("Tampering with request to server fields")
        var tampered = BYZ_MAX_TIMES_TAMPER
        Report.ReportRequest.newBuilder().apply {
            id = if (tampered > 0 && Random.nextBoolean()) {
                tampered--
                Random.nextInt(BYZ_MAX_ID_TAMPER)
            } else {
                info.id
            }

            epoch = if (tampered > 0 && Random.nextBoolean()) {
                tampered--
                Random.nextInt(BYZ_MAX_EP_TAMPER)
            } else {
                info.epoch
            }

            location = Report.Coordinates.newBuilder().apply {
                x = if (tampered > 0 && Random.nextBoolean()) {
                    tampered--
                    Random.nextInt(BYZ_MAX_COORDS_TAMPER)
                } else {
                    info.position.x
                }
                y = if (tampered > 0 && Random.nextBoolean()) {
                    Random.nextInt(BYZ_MAX_COORDS_TAMPER)
                } else {
                    info.position.y
                }
            }.build()

            signature = if (tampered > 0 && Random.nextBoolean()) {
                tampered--
                Base64.getEncoder().encodeToString(
                    Random.nextBytes(
                        BYZ_BYTES_TAMPER
                    )
                )
            } else {
                try {
                    val sig: Signature = Signature.getInstance("SHA256withECDSA")
                    sig.initSign(info.key)
                    sig.update("${info.id}${info.epoch}${info.position}".toByteArray())
                    Base64.getEncoder().encodeToString(sig.sign())
                } catch (e: SignatureException) {
                    println("Couldn't sign message")
                    return
                }
            }

            addAllProofs(responses.stream().map {
                Report.LocationProof.newBuilder().apply {
                    requesterId = if (tampered > 0 && Random.nextBoolean()) {
                        tampered--
                        Random.nextInt(BYZ_MAX_ID_TAMPER)
                    } else {
                        it.requester
                    }

                    proverId = if (tampered > 0 && Random.nextBoolean()) {
                        Random.nextInt(BYZ_MAX_ID_TAMPER)
                    } else {
                        it.prover
                    }

                    epoch = if (tampered > 0 && Random.nextBoolean()) {
                        Random.nextInt(BYZ_MAX_EP_TAMPER)
                    } else {
                        it.epoch
                    }

                    signature = if (tampered > 0 && Random.nextBoolean()) {
                        Base64.getEncoder().encodeToString(
                            Random.nextBytes(
                                BYZ_BYTES_TAMPER
                            )
                        )
                    } else {
                        it.signature
                    }
                }.build()
            }.toList())
        }.build()
    } else {
        // Non-byzantine request
        Report.ReportRequest.newBuilder().apply {
            id = info.id
            epoch = info.epoch
            location = Report.Coordinates.newBuilder().apply {
                x = info.position.x
                y = info.position.y
            }.build()

            signature = try {
                val sig: Signature = Signature.getInstance("SHA256withECDSA")
                sig.initSign(info.key)
                sig.update("${info.id}${info.epoch}${info.position}".toByteArray())
                Base64.getEncoder().encodeToString(sig.sign())
            } catch (e: SignatureException) {
                println("Couldn't sign message")
                return
            }

            addAllProofs(responses.stream().map {
                Report.LocationProof.newBuilder().apply {
                    requesterId = it.requester
                    proverId = it.prover
                    epoch = it.epoch
                    signature = it.signature
                }.build()
            }.toList())

        }.build()
    }


    // Send request to server
    val serverStub = LocationGrpcKt.LocationCoroutineStub(serverChannel)

    if (serverStub.locationReport(serverRequest).ack) {
        println("Request was OK")
    } else {
        println("BUSTED")
    }

    // Byzantine Level 0: Create non-existent request
    if (info.byzantineLevel >= 0 && Random.nextInt(100) < BYZ_PROB_DUMB) {
        println("Forging location proof")

        val user = info.board.getRandomUser()

        if (serverStub.locationReport(Report.ReportRequest.newBuilder().apply {
                id = info.id
                epoch = info.epoch

                location = Report.Coordinates.newBuilder().apply {
                    x = user.coords.x
                    y = user.coords.y
                }.build()

                signature = try {
                    val sig: Signature = Signature.getInstance("SHA256withECDSA")
                    sig.initSign(info.key)
                    sig.update("${user.id}${info.epoch}${user.coords}".toByteArray())
                    Base64.getEncoder().encodeToString(sig.sign())
                } catch (e: SignatureException) {
                    println("Couldn't sign message")
                    return
                }

                addProofs(Report.LocationProof.newBuilder().apply {
                    requesterId = user.id
                    proverId = info.id
                    epoch = info.epoch

                    signature = try {
                        val sig: Signature = Signature.getInstance("SHA256withECDSA")
                        sig.initSign(info.key)
                        sig.update("${user.id}${info.id}${info.epoch}".toByteArray())
                        Base64.getEncoder().encodeToString(sig.sign())
                    } catch (e: SignatureException) {
                        println("Couldn't sign message")
                        return
                    }
                }.build())

            }.build()).ack) {
            println("Request was OK :O")
        } else {
            println("Dumb user busted")
        }
    }
}