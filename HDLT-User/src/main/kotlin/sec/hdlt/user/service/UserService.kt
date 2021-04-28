package sec.hdlt.user.service

import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.delay
import sec.hdlt.protos.user.LocationProofGrpcKt
import sec.hdlt.protos.user.User
import sec.hdlt.user.*
import sec.hdlt.user.domain.Database
import sec.hdlt.user.domain.EpochInfo
import java.security.SignatureException

const val WAIT_TIME: Long = MAX_GRPC_TIME * 100
const val MAX_WAIT: Long = MAX_GRPC_TIME * 1000 / WAIT_TIME

class UserService : LocationProofGrpcKt.LocationProofCoroutineImplBase() {
    override suspend fun requestLocationProof(request: User.LocationProofRequest): User.LocationProofResponse {
        try {
            // Ignore request from own user
            if (Database.id == request.id) {
                return User.LocationProofResponse.getDefaultInstance()
            }

            // Byzantine Level 3: Ignore location request
            if (Database.byzantineLevel >= 3 && Database.random.nextInt(100) < BYZ_PROB_REJ_REQ) {
                println("Rejecting user location")
                return User.LocationProofResponse.getDefaultInstance()
            }

            val max = Database.epochs.keys.maxOrNull()
            if (max != null && max + MAX_EPOCH_AHEAD >= request.epoch) {
                var num = 0
                while (Database.epochs.keys.maxOrNull()!! < request.epoch && num < MAX_WAIT) {
                    delay(WAIT_TIME)
                    num++
                }

                if (num >= MAX_WAIT) {
                    println("Communication expired")
                    return User.LocationProofResponse.getDefaultInstance()
                }
            } else {
                println("User in epoch way ahead (${request.epoch} vs ${Database.epochs.keys.maxOrNull()})")
                return User.LocationProofResponse.getDefaultInstance()
            }

            val info: EpochInfo
            try {
                info = Database.epochs[request.epoch]!!
            } catch (e: NullPointerException) {
                return User.LocationProofResponse.getDefaultInstance()
            }

            // Byzantine Level 4: Redirect request to other user
            if (Database.byzantineLevel >= 4 && Database.random.nextInt(100) < BYZ_PROB_PASS_REQ) {
                val user = info.board.getRandomUser()
                println("Redirecting request from ${request.id} to ${user.id}")


                val channel = ManagedChannelBuilder.forAddress("localhost", user.port).usePlaintext().build()
                val stub = LocationProofGrpcKt.LocationProofCoroutineStub(channel)

                val response: User.LocationProofResponse = try {
                    stub.requestLocationProof(request)
                } catch (e: StatusException) {
                    println("Exception occurred on redirect, sending empty request")
                    User.LocationProofResponse.getDefaultInstance()
                }
                
                channel.shutdownNow()

                return response
            }

            // Byzantine Level 5: No verification of data
            if (Database.byzantineLevel >= 5 && Database.random.nextInt(100) < BYZ_PROB_NO_VER) {
                // Skip verification
            } else {
                try {
                    if (!verifySignature(
                            Database.keyStore.getCertificate(KEY_ALIAS_PREFIX + request.id),
                            "${request.id}${request.epoch}",
                            request.signature
                        )
                    ) {
                        println("INVALID SIGNATURE DETECTED")
                        throw StatusRuntimeException(Status.CANCELLED)
                    }
                } catch (e: SignatureException) {
                    println("INVALID SIGNATURE DETECTED")
                    throw StatusRuntimeException(Status.CANCELLED)
                } catch (e: IllegalArgumentException) {
                    println("INVALID BASE64 DETECTED")
                    throw StatusRuntimeException(Status.CANCELLED)
                } catch (e: NullPointerException) {
                    println("INVALID USER ${request.id} DETECTED")
                    throw StatusRuntimeException(Status.CANCELLED)
                }

                // Check if user is near
                if (!info.position.isNear(info.board.getUserCoords(request.id))) {
                    return User.LocationProofResponse.getDefaultInstance()
                } else if (info.users.contains(request.id)) {
                    println("Repeated request from user ${request.id} for epoch ${request.id}")
                    return User.LocationProofResponse.getDefaultInstance()
                }
            }

            info.users.add(request.id)

            return User.LocationProofResponse.newBuilder().apply {
                requesterId = request.id
                epoch = info.epoch
                proverId = Database.id

                // Send response with new signature
                signature = try {
                    sign(Database.key, "${request.id}${Database.id}${info.epoch}")
                } catch (e: SignatureException) {
                    println("Couldn't sign message")
                    throw StatusRuntimeException(Status.UNAVAILABLE)
                }
            }.build()
        } catch (e: StatusRuntimeException) {
            if (e.status.code == Status.CANCELLED.code) {
                println("CANCELLED BY CLIENT")
            } else {
                println("UNKNOWN ERROR")
            }
            return User.LocationProofResponse.getDefaultInstance()
        }
    }
}