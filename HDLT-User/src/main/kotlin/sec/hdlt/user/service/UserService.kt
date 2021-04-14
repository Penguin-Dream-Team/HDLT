package sec.hdlt.user.service

import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.delay
import sec.hdlt.protos.user.LocationProofGrpcKt
import sec.hdlt.protos.user.User
import sec.hdlt.user.*
import sec.hdlt.user.domain.Database
import java.security.SignatureException
import kotlin.random.Random

const val WAIT_TIME: Long = MAX_GRPC_TIME * 100
const val MAX_WAIT: Long = MAX_GRPC_TIME * 1000 / WAIT_TIME

class UserService : LocationProofGrpcKt.LocationProofCoroutineImplBase() {
    override suspend fun requestLocationProof(request: User.LocationProofRequest): User.LocationProofResponse {
        // Byzantine Level 3: Ignore location request
        if (Database.byzantineLevel >= 3 && Random.nextInt(100) < BYZ_PROB_REJ_REQ) {
            println("Rejecting user location")
            return User.LocationProofResponse.getDefaultInstance()
        }

        val max = Database.epochs.keys.maxOrNull()
        if (max != null && max + MAX_EPOCH_AHEAD >= request.epoch) {
            var num = 0
            while (Database.epochs.keys.maxOrNull()!! < request.epoch && num < MAX_WAIT) {
                delay(MAX_GRPC_TIME * 100)
                num++
            }

            if (num >= MAX_WAIT) {
                println("Communication expired")
                return User.LocationProofResponse.getDefaultInstance()
            }
        } else {
            println("User in epoch way ahead")
            return User.LocationProofResponse.getDefaultInstance()
        }

        val info = Database.epochs[request.epoch]!!

        // Byzantine Level 4: Redirect request to other user
        if (Database.byzantineLevel >= 4 && Random.nextInt(100) < BYZ_PROB_PASS_REQ) {
            println("Redirecting request")

            val user = info.board.getRandomUser()

            val stub = LocationProofGrpcKt.LocationProofCoroutineStub(
                ManagedChannelBuilder.forAddress("localhost", user.port).build()
            )

            return stub.requestLocationProof(request)
        }

        // Byzantine Level 5: No verification of data
        if (Database.byzantineLevel >= 5 && Random.nextInt(100) < BYZ_PROB_NO_VER) {
            // Skip verification
        } else {
            try {
                if (!verifySignature(Database.keyStore.getCertificate(KEY_ALIAS_PREFIX + request.id), "${request.id}${request.epoch}", request.signature)) {
                    println("INVALID SIGNATURE DETECTED")
                    throw StatusRuntimeException(Status.CANCELLED)
                }
            } catch (e: SignatureException) {
                println("INVALID SIGNATURE DETECTED")
                throw StatusRuntimeException(Status.CANCELLED)
            } catch (e: IllegalArgumentException) {
                println("INVALID BASE64 DETECTED")
                throw StatusRuntimeException(Status.CANCELLED)
            }

            // Check if user is near
            if (!info.position.isNear(info.board.getUserCoords(request.id)) || info.users.contains(request.id)) {
                throw StatusRuntimeException(Status.FAILED_PRECONDITION)
            }
        }

        return if (Database.id == request.id) {
            User.LocationProofResponse.getDefaultInstance()
        } else {
            info.users.add(request.id)

            User.LocationProofResponse.newBuilder().apply {
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
        }
    }
}