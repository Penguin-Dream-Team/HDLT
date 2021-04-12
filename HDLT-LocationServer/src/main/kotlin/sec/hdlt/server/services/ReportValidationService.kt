package sec.hdlt.server.services

import sec.hdlt.server.KEY_USER_PREFIX
import sec.hdlt.server.data.Proof
import java.security.KeyStore
import java.security.Signature
import java.security.SignatureException
import java.util.*

class ReportValidationService(private val keyStore: KeyStore) {

    fun validateSignature(
        user: Int,
        epoch: Int,
        sig: String
    ): Boolean {
        return try {
            val signature: Signature = Signature.getInstance("SHA256withRSA")
            signature.initVerify(keyStore.getCertificate(KEY_USER_PREFIX + user))
            signature.update("${user}${epoch}".toByteArray())
            signature.verify(Base64.getDecoder().decode(sig))
            true
        } catch (e: SignatureException) {
            println("Invalid signature detected for user $user")
            false

        } catch (e: IllegalArgumentException) {
            println("Invalid base64 detected for user $user")
            false
        }
    }

    fun validateRequest(
        user: Int,
        epoch: Int,
        proofs: List<Proof>
    ): Boolean {
        proofs.forEach { proof ->
            if (validateSignature(proof.prover, proof.epoch, proof.signature)) {
                if (user != proof.requester ||
                    user == proof.prover ||
                    epoch != proof.epoch
                ) {
                    println("Invalid proof for user $user sent by user ${proof.prover} on epoch $epoch")
                    return false
                }
            } else return false
        }
        return true
    }
}