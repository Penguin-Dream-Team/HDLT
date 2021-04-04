package sec.hdlt.utils

import java.io.InputStream
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec


object SecureUtils {

    fun readPrivateKey(privEncoded: ByteArray): PrivateKey {
        val privSpec = PKCS8EncodedKeySpec(privEncoded)
        val keyFacPriv = KeyFactory.getInstance("RSA")
        return keyFacPriv.generatePrivate(privSpec)
    }

    fun readCertificate(pubFis: InputStream): X509Certificate {
        val cf = CertificateFactory.getInstance("X.509")
        val cert: X509Certificate = cf.generateCertificate(pubFis) as X509Certificate
        pubFis.close()
        return cert
    }
}