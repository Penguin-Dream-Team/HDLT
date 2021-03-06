package sec.hdlt.user.services

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sec.hdlt.protos.master.HDLTMasterGrpcKt
import sec.hdlt.protos.master.Master
import sec.hdlt.protos.user.LocationProofGrpcKt
import sec.hdlt.protos.user.User
import sec.hdlt.user.*
import sec.hdlt.user.domain.*
import sec.hdlt.user.dto.ProofDto
import sec.hdlt.user.dto.ReportDto
import java.security.SignatureException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.streams.toList

/**
 * Class to handle connections from the Master Server (initialization and new epochs)
 */
class MasterService : HDLTMasterGrpcKt.HDLTMasterCoroutineImplBase() {
    private val mutex: Mutex = Mutex()

    /**
     * Deal with Master Server init request, allowing to initialize Random with a seed and the ServerFrontend class
     */
    override suspend fun init(request: Master.InitRequest): Master.InitResponse {
        Database.initRandom(request.randomSeed)
        Database.initServer(locationHost, locationPort, request.serverNum, request.serverByzantine)

        return Master.InitResponse.getDefaultInstance()
    }

    /**
     * Deal with epoch change and new board information
     */
    override suspend fun broadcastEpoch(request: Master.BroadcastEpochRequest): Master.BroadcastEpochResponse {
        println("Receiving epoch ${request.epoch}")

        val board = Board()

        // Fill board
        request.cellsList.stream()
            .forEach { board.addUser(UserInfo(it.userId, Coordinates(it.x, it.y))) }

        val info = EpochInfo(board.getUserCoords(Database.id), request.epoch, board, mutableSetOf())

        mutex.withLock {
            Database.epochs.put(request.epoch, info)
        }

        GlobalScope.launch {
            communicate(info)
        }

        return Master.BroadcastEpochResponse.newBuilder().apply {
            userId = Database.id
            ok = true
        }.build()
    }
}

suspend fun communicate(info: EpochInfo) {
    // Byzantine Level 1: Skip communication
    if (Database.byzantineLevel >= 1 && Database.random.nextInt(100) < BYZ_PROB_NOT_SEND) {
        return
    }

    val users: List<UserInfo> = info.board.getAllUsers()

    // Byzantine Level 2: Tamper with fields
    // Create request once (will be equal for all gRPC calls)
    val request = User.LocationProofRequest.newBuilder().apply {
        id = if (Database.byzantineLevel >= 2 && Database.random.nextInt(100) < BYZ_PROB_TAMPER) {
            println("[EPOCH ${info.epoch}] Tampering with id")
            Database.random.nextInt(BYZ_MAX_ID_TAMPER)
        } else {
            Database.id
        }
        epoch = if (Database.byzantineLevel >= 2 && Database.random.nextInt(100) < BYZ_PROB_TAMPER) {
            println("[EPOCH ${info.epoch}] Tampering with epoch")
            Database.random.nextInt(BYZ_MAX_EP_TAMPER)
        } else {
            info.epoch
        }

        signature = if (Database.byzantineLevel >= 2 && Database.random.nextInt(100) < BYZ_PROB_TAMPER) {
            println("[EPOCH ${info.epoch}] Tampering with signature")
            Base64.getEncoder().encodeToString(Database.random.nextBytes(BYZ_BYTES_TAMPER))
        } else {
            try {
                sign(Database.key, "${Database.id}${info.epoch}")
            } catch (e: SignatureException) {
                println("[EPOCH ${info.epoch}] Couldn't sign message")
                return
            }
        }
    }.build()

    val mutex = Mutex()
    val proofs = mutableListOf<ProofDto>()

    // Launch call for each user
    coroutineScope {
        for (user: UserInfo in users) {
            launch {
                val userChannel: ManagedChannel =
                    ManagedChannelBuilder.forAddress("localhost", user.port).usePlaintext().build()
                val userStub = LocationProofGrpcKt.LocationProofCoroutineStub(userChannel)
                    .withDeadlineAfter(MAX_GRPC_TIME, TimeUnit.SECONDS)

                val response: User.LocationProofResponse

                try {
                    response = userStub.requestLocationProof(request)
                    userChannel.shutdownNow()
                } catch (e: StatusException) {
                    when (e.status.code) {
                        Status.CANCELLED.code -> {
                            println("[EPOCH ${info.epoch} PROVER ${user.id}] Prover detected invalid signature")
                        }
                        Status.UNAUTHENTICATED.code -> {
                            println("[EPOCH ${info.epoch} PROVER ${user.id}] Prover couldn't deliver message")
                        }
                        Status.INVALID_ARGUMENT.code -> {
                            println("[EPOCH ${info.epoch} PROVER ${user.id}] Prover is in different epoch")
                        }
                        Status.DEADLINE_EXCEEDED.code -> {
                            println("[EPOCH ${info.epoch} PROVER ${user.id}] Prover took too long to answer")
                        }
                        else -> {
                            println("[EPOCH ${info.epoch} PROVER ${user.id}] Unknown error"); }
                    }

                    userChannel.shutdownNow()
                    return@launch
                } catch (e: Exception) {
                    println("[EPOCH ${info.epoch} PROVER ${user.id}] UNKNOWN EXCEPTION")
                    e.printStackTrace()
                    userChannel.shutdownNow()
                    return@launch
                }

                // Byzantine Level 5: No verification of data
                if (Database.byzantineLevel >= 5 && Database.random.nextInt(100) < BYZ_PROB_NO_VER) {
                    // Skip verifications
                } else {
                    // Check response
                    if (response.epoch == 0 && response.proverId == 0 && response.requesterId == 0 && response.signature.equals(
                            ""
                        )
                    ) {
                        return@launch
                    } else if (user.id != response.proverId || Database.id != response.requesterId) {
                        println("[EPOCH ${info.epoch} PROVER ${user.id}] User ids do not match")
                        return@launch
                    } else if (info.epoch != response.epoch) {
                        println("[EPOCH ${info.epoch} PROVER ${user.id}] Prover is in different epoch")
                        return@launch
                    } else if (!info.position.isNear(info.board.getUserCoords(user.id))) {
                        println("[EPOCH ${info.epoch} PROVER ${user.id}] Prover is far")
                        return@launch
                    }

                    // Check signature
                    try {
                        if (!verifySignature(
                                Database.keyStore.getCertificate(KEY_ALIAS_PREFIX + user.id),
                                "${Database.id}${user.id}${info.epoch}",
                                response.signature
                            )
                        ) {
                            println("[EPOCH ${info.epoch} PROVER ${user.id}] Invalid signature detected")
                            userChannel.shutdownNow()
                            return@launch
                        }
                    } catch (e: SignatureException) {
                        println("[EPOCH ${info.epoch} PROVER ${user.id}] Invalid signature detected")
                        return@launch
                    } catch (e: IllegalArgumentException) {
                        println("[EPOCH ${info.epoch} PROVER ${user.id}] Invalid base64 detected")
                        return@launch
                    }

                    mutex.withLock {
                        proofs.add(
                            ProofDto(
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

    if (proofs.size == 0) {
        return
    }

    // Byzantine Level 2: Tamper with fields
    val report: ReportDto = if (Database.byzantineLevel >= 2 && Database.random.nextInt(100) < BYZ_PROB_TAMPER) {
        println("[EPOCH ${info.epoch}] Tampering with request to server fields")
        var tampered = BYZ_MAX_TIMES_TAMPER

        ReportDto(
            // Id
            if (tampered > 0 && Database.random.nextBoolean()) {
                tampered--
                Database.random.nextInt(BYZ_MAX_ID_TAMPER)
            } else {
                Database.id
            },

            // Epoch
            if (tampered > 0 && Database.random.nextBoolean()) {
                tampered--
                Database.random.nextInt(BYZ_MAX_EP_TAMPER)
            } else {
                info.epoch
            },

            // Location
            Coordinates(
                // X coord
                if (tampered > 0 && Database.random.nextBoolean()) {
                    tampered--
                    Database.random.nextInt(BYZ_MAX_COORDS_TAMPER)
                } else {
                    info.position.x
                },

                // Y coord
                if (tampered > 0 && Database.random.nextBoolean()) {
                    Database.random.nextInt(BYZ_MAX_COORDS_TAMPER)
                } else {
                    info.position.y
                }
            ),

            // Signature
            if (tampered > 0 && Database.random.nextBoolean()) {
                tampered--
                Base64.getEncoder().encodeToString(
                    Database.random.nextBytes(
                        BYZ_BYTES_TAMPER
                    )
                )
            } else {
                try {
                    sign(Database.key, "${Database.id}${info.epoch}${info.position}")
                } catch (e: SignatureException) {
                    println("[EPOCH ${info.epoch}] Couldn't sign message")
                    return
                }
            },

            // Location proofs
            proofs.stream().map {
                ProofDto(
                    // Requester Id
                    if (tampered > 0 && Database.random.nextBoolean()) {
                        tampered--
                        Database.random.nextInt(BYZ_MAX_ID_TAMPER)
                    } else {
                        it.requester
                    },

                    // Prover Id
                    if (tampered > 0 && Database.random.nextBoolean()) {
                        Database.random.nextInt(BYZ_MAX_ID_TAMPER)
                    } else {
                        it.prover
                    },

                    // Epoch
                    if (tampered > 0 && Database.random.nextBoolean()) {
                        Database.random.nextInt(BYZ_MAX_EP_TAMPER)
                    } else {
                        it.epoch
                    },

                    // Signature
                    if (tampered > 0 && Database.random.nextBoolean()) {
                        Base64.getEncoder().encodeToString(
                            Database.random.nextBytes(
                                BYZ_BYTES_TAMPER
                            )
                        )
                    } else {
                        it.signature
                    }
                )
            }.toList()
        )
    } else {
        // Non-byzantine request
        ReportDto(
            Database.id,
            info.epoch,
            info.position,

            // Signature
            try {
                sign(Database.key, "${Database.id}${info.epoch}${info.position}")
            } catch (e: SignatureException) {
                println("[EPOCH ${info.epoch}] Couldn't sign message")
                return
            },

            // Location Proofs
            proofs
        )
    }

    // Send request to server
    val response = Database.frontend.submitReport(report)
    println(if (response) "[EPOCH ${info.epoch}] Report ACCEPTED by majority of servers" else "[EPOCH ${info.epoch}] Report REJECTED by majority of servers")

    // Byzantine Level 0: Create non-existent request
    if (Database.byzantineLevel >= 0 && Database.random.nextInt(100) < BYZ_PROB_DUMB) {
        println("[EPOCH ${info.epoch}] Forging location proof")

        val user = info.board.getRandomUser()

        val forgedReport = ReportDto(
            // Id
            Database.id,

            // Epoch
            info.epoch,

            // Location
            user.coords,

            // Signature
            try {
                sign(Database.key, "${Database.id}${info.epoch}${user.coords}")
            } catch (e: SignatureException) {
                println("[EPOCH ${info.epoch}] Couldn't sign message")
                return
            },

            mutableListOf(
                ProofDto(
                    // Requester
                    user.id,

                    // Prover
                    Database.id,

                    // Epoch
                    info.epoch,

                    // Signature
                    try {
                        sign(Database.key, "${user.id}${Database.id}${info.epoch}")
                    } catch (e: SignatureException) {
                        println("[EPOCH ${info.epoch}] Couldn't sign message")
                        return
                    }
                )
            )
        )

        val forgedResponse = Database.frontend.submitReport(forgedReport)

        println(if (forgedResponse) "[EPOCH ${info.epoch}] FORGED report ACCEPTED by majority of servers" else "[EPOCH ${info.epoch}] FORGED report REJECTED by majority of servers")
    }
}
