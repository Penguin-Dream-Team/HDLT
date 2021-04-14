package sec.hdlt.server.services

import sec.hdlt.server.KEY_HA_ALIAS
import sec.hdlt.server.KEY_USER_PREFIX
import sec.hdlt.server.data.Coordinates
import sec.hdlt.server.data.Database
import sec.hdlt.server.data.Proof
import sec.hdlt.server.verifySignature
import java.security.SignatureException

class ReportValidationService {
    companion object {
        private fun validateSignatureImpl(user: Int, sig: String, format: String, requester: Int): Boolean {
            return try {
                verifySignature(Database.keyStore.getCertificate(KEY_USER_PREFIX + user), format, sig)
            } catch (e: SignatureException) {
                println("Invalid signature detected for user $requester")
                false

            } catch (e: IllegalArgumentException) {
                println("Invalid base64 detected for user $requester")
                false
            }
        }

        private fun validateHASignatureImpl(
            format: String,
            sig: String,
        ): Boolean {
            return try {
                verifySignature(Database.keyStore.getCertificate(KEY_HA_ALIAS), format, sig)
            } catch (e: SignatureException) {
                println("Invalid signature detected for HA")
                false

            } catch (e: IllegalArgumentException) {
                println("Invalid base64 detected for HA")
                false
            }
        }

        fun validateHASignature(
            user: Int,
            epoch: Int,
            sig: String
        ): Boolean {
            return validateHASignatureImpl("$user$epoch", sig)
        }

        fun validateSignature(
            user: Int,
            epoch: Int,
            position: Coordinates,
            sig: String
        ): Boolean {
            return validateSignatureImpl(user, sig, "${user}${epoch}${position}", user)
        }

        fun validateSignature(
            user: Int,
            epoch: Int,
            sig: String
        ): Boolean {
            return validateSignatureImpl(user, sig, "${user}${epoch}", user)
        }

        fun validateSignature(
            user: Int,
            prover: Int,
            epoch: Int,
            sig: String
        ): Boolean {
            return validateSignatureImpl(prover, sig, "${user}${prover}${epoch}", user)
        }

        fun validateRequest(
            user: Int,
            epoch: Int,
            proofs: List<Proof>
        ): Boolean {
            val provers: MutableSet<Int> = mutableSetOf()
            proofs.forEach { proof ->
                // FIXME: Just ignore this signature and move on?
                if (validateSignature(proof.requester, proof.prover, proof.epoch, proof.signature)) {
                    if (user != proof.requester ||
                        user == proof.prover ||
                        epoch != proof.epoch ||
                        provers.contains(proof.prover)
                    ) {
                        println("Invalid proof for user $user sent by user ${proof.prover} on epoch $epoch")
                        return false
                    }

                    provers.add(proof.prover)
                } else {
                    return false
                }
            }
            return true
        }
    }
}