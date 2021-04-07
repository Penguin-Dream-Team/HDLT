package sec.hdlt.user.service

import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import sec.hdlt.protos.user.LocationProofGrpcKt
import sec.hdlt.protos.user.User
import sec.hdlt.user.BYZ_PROB_NO_VER
import sec.hdlt.user.BYZ_PROB_PASS_REQ
import sec.hdlt.user.BYZ_PROB_REJ_REQ
import sec.hdlt.user.KEY_ALIAS_PREFIX
import sec.hdlt.user.domain.EpochInfo
import java.security.Signature
import java.security.SignatureException
import java.util.*
import kotlin.random.Random

class UserService(private val info: EpochInfo) : LocationProofGrpcKt.LocationProofCoroutineImplBase() {
    override suspend fun requestLocationProof(request: User.LocationProofRequest): User.LocationProofResponse {
        // Byzantine Level 3: Ignore location request
        if (info.byzantineLevel >= 3 && Random.nextInt(100) < BYZ_PROB_REJ_REQ) {
            println("Rejecting user location")
            return User.LocationProofResponse.getDefaultInstance()
        }

        // Byzantine Level 6: Redirect request to other user
        if (info.byzantineLevel >= 6 && Random.nextInt(100) < BYZ_PROB_PASS_REQ) {
            println("Redirecting request")

            val user = info.board.getRandomUser()

            val stub = LocationProofGrpcKt.LocationProofCoroutineStub(
                ManagedChannelBuilder.forAddress("localhost", user.port).build()
            )

            return stub.requestLocationProof(request)
        }

        // Check signature
        var sig: Signature = Signature.getInstance("SHA256withECDSA")

        // Byzantine Level 8: No verification of data
        if (info.byzantineLevel >= 8 && Random.nextInt(100) < BYZ_PROB_NO_VER) {
            // Skip verification
        }else {

            try {
                sig.initVerify(info.keyStore.getCertificate(KEY_ALIAS_PREFIX + request.id))
                sig.update("${request.id}${request.epoch}".toByteArray())
                sig.verify(Base64.getDecoder().decode(request.signature))
            } catch (e: SignatureException) {
                println("INVALID SIGNATURE DETECTED")
                throw StatusRuntimeException(Status.CANCELLED)
            } catch (e: IllegalArgumentException) {
                println("INVALID BASE64 DETECTED")
                throw StatusRuntimeException(Status.CANCELLED)
            }

            // Check if user is near
            if (!info.position.isNear(info.board.getUserCoords(request.id))) {
                println("User not near")
                throw StatusRuntimeException(Status.FAILED_PRECONDITION)
            } else if (info.epoch != request.epoch) {
                println("Wrong epoch")
                throw StatusRuntimeException(Status.INVALID_ARGUMENT)
            }
        }

        // Send response with new signature
        sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(info.key)
        sig.update("${request.id}${info.id}${info.epoch}".toByteArray())

        return User.LocationProofResponse.newBuilder().apply {
            requesterId = request.id
            epoch = info.epoch
            proverId = info.id
            signature = try {
                Base64.getEncoder().encodeToString(sig.sign())
            } catch (e: SignatureException) {
                println("Couldn't sign message")
                throw StatusRuntimeException(Status.UNAVAILABLE)
            }
        }.build()
    }
}