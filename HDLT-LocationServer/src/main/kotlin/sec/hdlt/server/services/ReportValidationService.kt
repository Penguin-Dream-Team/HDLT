package sec.hdlt.server.services

import sec.hdlt.server.KEY_ALIAS_PREFIX
import sec.hdlt.server.data.Coordinates
import java.security.KeyStore
import java.security.Signature
import java.security.SignatureException
import java.util.*

@Suppress("NAME_SHADOWING")
class ReportValidationService(val keyStore: KeyStore) {

    fun validateRequest(
        user1: Int,
        user2: Int,
        coordinates1: Coordinates,
        coordinates2: Coordinates
    ): Boolean {
        if (!coordinates1.isNear(coordinates2) || user1 == user2)
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
            val signature1: Signature = Signature.getInstance("SHA256withECDSA")
            signature1.initVerify(keyStore.getCertificate(KEY_ALIAS_PREFIX + user1))
            signature1.update("${user1}${user2}${epoch}".toByteArray())

            val signature2: Signature = Signature.getInstance("SHA256withECDSA")
            signature2.initVerify(keyStore.getCertificate(KEY_ALIAS_PREFIX + user2))
            signature2.update("${user1}${user2}${epoch}".toByteArray())

            signature1.verify(Base64.getDecoder().decode(sig1)) && signature2.verify(Base64.getDecoder().decode(sig2))
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
            val signature: Signature = Signature.getInstance("SHA256withECDSA")
            signature.initVerify(keyStore.getCertificate(KEY_ALIAS_PREFIX + user))
            signature.update("${user}${epoch}".toByteArray())
            signature.verify(Base64.getDecoder().decode(sig))
        } catch (e: SignatureException) {
            println("Invalid signature detected")
            false

        } catch (e: IllegalArgumentException) {
            println("Invalid base64 detected")
            false
        }
    }
}