package sec.hdlt.server.services

import sec.hdlt.server.KEY_HA_ALIAS
import sec.hdlt.server.KEY_USER_PREFIX
import sec.hdlt.server.domain.Coordinates
import sec.hdlt.server.domain.Database
import sec.hdlt.server.domain.Proof
import sec.hdlt.server.verifySignature
import java.security.SignatureException

class RequestValidationService {
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
            } catch (e: NullPointerException) {
                println("Invalid user with id $user")
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

        fun validateHASignature(
            epoch: Int,
            coords: Coordinates,
            sig: String
        ): Boolean {
            return validateHASignatureImpl("$coords$epoch", sig)
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

        fun getValidProofs(
            user: Int,
            epoch: Int,
            proofs: List<Proof>
        ): List<Proof> {
            val provers: MutableSet<Int> = mutableSetOf()
            return proofs.filter { proof ->
                if (validateSignature(proof.requester, proof.prover, proof.epoch, proof.signature)) {
                    if (user != proof.requester ||
                        user == proof.prover ||
                        epoch != proof.epoch ||
                        provers.contains(proof.prover)
                    ) {
                        println("Invalid proof for user $user sent by user ${proof.prover} on epoch $epoch")
                        false
                    } else {
                        provers.add(proof.prover)

                        true
                    }
                } else {
                    false
                }
            }.toList()
        }
    }
}