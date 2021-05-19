package sec.hdlt.server

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.Certificate
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


const val SYM_NONCE_LEN = 12
const val SYM_KEY_SIZE = 256

fun generateKey(): SecretKey {
    val generator: KeyGenerator = KeyGenerator.getInstance("ChaCha20")
    generator.init(SYM_KEY_SIZE, SecureRandom.getInstanceStrong())

    return generator.generateKey()
}

fun generateNonce(): ByteArray {
    val nonce = ByteArray(SYM_NONCE_LEN)
    SecureRandom().nextBytes(nonce)
    return nonce
}

fun asymmetricCipher(key: Certificate, plaintext: String): String {
    val cipher: Cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
    cipher.init(Cipher.ENCRYPT_MODE, key)

    return Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.toByteArray()))
}

fun symmetricCipher(key: SecretKey, plaintext: String, nonce: ByteArray): String {
    val cipher: Cipher = Cipher.getInstance("ChaCha20-Poly1305")
    val iv = IvParameterSpec(nonce)
    cipher.init(Cipher.ENCRYPT_MODE, key, iv)

    return Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.toByteArray()))
}

fun asymmetricDecipher(key: PrivateKey, ciphertext: String): SecretKey {
    val cipher: Cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
    cipher.init(Cipher.DECRYPT_MODE, key)

    return SecretKeySpec(Base64.getDecoder().decode(cipher.doFinal(Base64.getDecoder().decode(ciphertext))), 0, SYM_KEY_SIZE / 8, "ChaCha20")
}

fun symmetricDecipher(key: SecretKey, nonce: ByteArray, ciphertext: String): String {
    val cipher: Cipher = Cipher.getInstance("ChaCha20-Poly1305")
    val iv = IvParameterSpec(nonce)
    cipher.init(Cipher.DECRYPT_MODE, key, iv)

    return String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)))
}

fun sign(key: PrivateKey, format: String): String {
    val sig: Signature = Signature.getInstance("SHA256withRSA")
    sig.initSign(key)
    sig.update(format.toByteArray())

    return Base64.getEncoder().encodeToString(sig.sign())
}

fun verifySignature(cert: Certificate, format: String, signature: String): Boolean {
    val sig: Signature = Signature.getInstance("SHA256withRSA")
    sig.initVerify(cert)
    sig.update(format.toByteArray())

    return sig.verify(Base64.getDecoder().decode(signature))
}

fun hash(input: MutableList<ByteArray>, nonce: Long): ByteArray {
    // add nonce
    input[input.size - 1] = ByteBuffer.allocate(8).putLong(nonce).array()

    val digest = MessageDigest
        .getInstance("SHA256")

    input.forEach {
        digest.update(it)
    }

    return digest.digest()
}
