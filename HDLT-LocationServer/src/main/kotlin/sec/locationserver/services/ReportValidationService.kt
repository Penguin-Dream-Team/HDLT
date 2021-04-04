package sec.locationserver.services

import sec.locationserver.data.Coordinates
import java.security.Signature
import java.security.SignatureException
import java.util.*

@Suppress("NAME_SHADOWING")
class ReportValidationService() {

    fun validateRequest(
        user1: Int,
        user2: Int,
        coordinates1: Coordinates,
        coordinates2: Coordinates
    ): Boolean {
        if (!coordinates1.isNear(coordinates2))
            return false

        return true
    }

    fun isByzantine(user: Int): Boolean {
        return true
    }

    fun validateSignature(
        user1: Int,
        user2: Int,
        epoch: Int,
        coordinates1: Coordinates,
        coordinates2: Coordinates,
        sig1: String,
        sig2: String
    ): Boolean {
        return try {
            val signature1: Signature = Signature.getInstance("SHA256withRSA")
            //signature1.initVerify(keyStore.getCertificate(KEY_ALIAS_PREFIX + user1))
            signature1.update(Base64.getDecoder().decode(sig1))
            signature1.verify("${user1}${user2}${epoch}${coordinates1}${coordinates2}".toByteArray())

            val signature2: Signature = Signature.getInstance("SHA256withRSA")
            //signature2.initVerify(keyStore.getCertificate(KEY_ALIAS_PREFIX + user2))
            signature2.update(Base64.getDecoder().decode(sig2))
            signature2.verify("${user2}${user1}${epoch}${coordinates2}${coordinates1}".toByteArray())

            true

        } catch (e: SignatureException) {
            println("Invalid signature detected")
            false

        } catch (e: IllegalArgumentException) {
            println("Invalid base64 detected")
            false
        }
    }

    fun validateSignature(
        user: Int,
        epoch: Int,
        sig: String
    ): Boolean {
        return try {
            val signature: Signature = Signature.getInstance("SHA256withRSA")
            //sig.initVerify(keyStore.getCertificate(KEY_ALIAS_PREFIX + user))
            signature.update(Base64.getDecoder().decode(sig))
            signature.verify("${user}${epoch}".toByteArray())
            true

        } catch (e: SignatureException) {
            println("Invalid signature detected")
            false

        } catch (e: IllegalArgumentException) {
            println("Invalid base64 detected")
            false
        }
    }
}