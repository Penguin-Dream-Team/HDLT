package sec.hdlt.server.services

import sec.hdlt.server.KEY_USER_PREFIX
import sec.hdlt.server.data.Coordinates
import sec.hdlt.server.data.Proof
import sec.hdlt.server.verifySignature
import java.lang.NullPointerException
import java.security.KeyStore
import java.security.Signature
import java.security.SignatureException
import java.util.*

class ReportValidationService(private val keyStore: KeyStore) {

    fun validateSignature(
        user: Int,
        epoch: Int,
        position: Coordinates,
        sig: String
    ): Boolean {
        return try {
            verifySignature(keyStore.getCertificate(KEY_USER_PREFIX + user), "${user}${epoch}${position}", sig)
        } catch (e: SignatureException) {
            println("Invalid signature detected for user $user")
            false

        } catch (e: IllegalArgumentException) {
            println("Invalid base64 detected for user $user")
            false
        }
    }

    fun validateSignature(
        user: Int,
        epoch: Int,
        sig: String
    ): Boolean {
        return try {
            verifySignature(keyStore.getCertificate(KEY_USER_PREFIX + user), "${user}${epoch}", sig)
        } catch (e: SignatureException) {
            println("Invalid signature detected for user $user")
            false

        } catch (e: IllegalArgumentException) {
            println("Invalid base64 detected for user $user")
            false
        }
    }

    fun validateSignature(
        user: Int,
        prover: Int,
        epoch: Int,
        sig: String
    ): Boolean {
        return try {
            verifySignature(keyStore.getCertificate(KEY_USER_PREFIX + prover), "${user}${prover}${epoch}", sig)
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
            if (validateSignature(proof.requester, proof.prover, proof.epoch, proof.signature)) {
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