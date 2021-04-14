package sec.hdlt.ca

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.crypto.params.AsymmetricKeyParameter
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.math.BigInteger
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.KeySpec
import java.util.*
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.security.auth.x500.X500Principal

const val CA_KS_PATH = "/keystore.jks"
const val CA_KS_ALT_PATH = "keystore.jks"
const val CA_KS_PASS = "SUP36S3C637P4SS"

const val CA_KEY_ALIAS = "CA_KEY"
const val CA_KEY_PASS = "4N07H36S3C637P4SS"
const val CA_KEY_SIZE = 4096

const val PBKDF2_ITER = 100001
const val PBKDF2_KEY_SIZE = 512

fun main() {
    print("SEC HDLT CA\nPath to user keystore: ")
    val userKeyStoreFile = readLine()!!
    print("Path to server keystore: ")
    val serverKeyStoreFile = readLine()!!
    print("Path to HA keystore: ")
    val haKeyStoreFile = readLine()!!
    print("User keystore password: ")
    val userKeyStorePass = readLine()!!
    print("Server keystore password: ")
    val serverKeyStorePass = readLine()!!
    print("HA keystore password: ")
    val haKeyStorePass = readLine()!!
    print("User certificate alias prefix: ")
    val userPrefix = readLine()!!
    print("Server certificate alias: ")
    val serverAlias = readLine()!!
    print("HA certificate alias: ")
    val haAlias = readLine()!!
    print("Server key pass: ")
    val serverKeyPass = readLine()!!
    print("HA key pass: ")
    val haKeyPass = readLine()!!
    print("Number of users to generate: ")
    val nUsers = readLine()!!.toInt()
    print("Password prefix of users: ")
    val passPrefix = readLine()!!
    print("Salt to use in passwords: ")
    val salt = readLine()!!

    // Access CA Keystore
    var CAKeyStoreFile: InputStream? = object {}.javaClass.getResourceAsStream(CA_KS_PATH)

    if (CAKeyStoreFile == null) {
        println("Initializing new CA")
        initCA()
        CAKeyStoreFile = FileInputStream(CA_KS_ALT_PATH)
    }

    val CAKeyStore: KeyStore = KeyStore.getInstance("jks")
    CAKeyStore.load(CAKeyStoreFile, CA_KS_PASS.toCharArray())
    val CACert: X509Certificate = CAKeyStore.getCertificate(CA_KEY_ALIAS) as X509Certificate
    val CAKey: PrivateKey = CAKeyStore.getKey(CA_KEY_ALIAS, CA_KEY_PASS.toCharArray()) as PrivateKey

    // Initialize User Keystore
    val userKeyStore: KeyStore = KeyStore.getInstance("jks")
    userKeyStore.load(null, userKeyStorePass.toCharArray())
    userKeyStore.setCertificateEntry("CA", CACert)

    // Initialize Server Keystore
    val serverKeyStore: KeyStore = KeyStore.getInstance("jks")
    serverKeyStore.load(null, serverKeyStorePass.toCharArray())
    serverKeyStore.setCertificateEntry("CA", CACert)

    // Initialize HA Keystore
    val haKeyStore: KeyStore = KeyStore.getInstance("jks")
    haKeyStore.load(null, haKeyStorePass.toCharArray())
    haKeyStore.setCertificateEntry("CA", CACert)

    // Generate Server Certificate
    println("Generating server key")
    val serverKP: KeyPair = generateKeyPair()
    val serverCertificate: X509Certificate =
        signCSR(generateCSR("sec.ist.pt", "HDLT-LocationServer", "SEC LLC", "Lisbon", "Lisbon", "PT", serverKP), CAKey)
    val serverCertificateChain: Array<X509Certificate> = Array(2) { i -> if (i % 2 == 0) serverCertificate else CACert }
    serverKeyStore.setKeyEntry(serverAlias, serverKP.private, serverKeyPass.toCharArray(), serverCertificateChain)
    userKeyStore.setCertificateEntry(serverAlias, serverCertificate)
    haKeyStore.setCertificateEntry(serverAlias, serverCertificate)

    // Generate HA Certificate
    println("Generating HA key")
    val haKP: KeyPair = generateKeyPair()
    val haCertificate: X509Certificate =
        signCSR(generateCSR("sec.ist.pt", "HDLT-HA", "SEC LLC", "Lisbon", "Lisbon", "PT", haKP), CAKey)
    val haCertificateChain: Array<X509Certificate> = Array(2) { i -> if (i % 2 == 0) haCertificate else CACert }
    haKeyStore.setKeyEntry(haAlias, haKP.private, haKeyPass.toCharArray(), haCertificateChain)
    serverKeyStore.setCertificateEntry(haAlias, haCertificate)

    // Generate User Certificates
    println("Generating user keys")
    for (i in 0 until nUsers) {
        val userPass = deriveKey(passPrefix + i, salt)
        val userKP: KeyPair = generateKeyPair()
        val userCertificate: X509Certificate =
            signCSR(generateCSR("sec.ist.pt", "HDLT-User$i", "SEC LLC", "Lisbon", "Lisbon", "PT", userKP), CAKey)
        val userCertificateChain: Array<X509Certificate> = Array(2) { i -> if (i % 2 == 0) userCertificate else CACert }
        userKeyStore.setKeyEntry(userPrefix + i, userKP.private, userPass.toCharArray(), userCertificateChain)
        userKeyStore.setCertificateEntry("cert_$userPrefix$i", userCertificate)
        serverKeyStore.setCertificateEntry("cert_$userPrefix$i", userCertificate)
        haKeyStore.setCertificateEntry("cert_$userPrefix$i", userCertificate)
    }

    // Save Server KeyStore
    println("Saving Server KeyStore to file")
    saveKeyStore(serverKeyStore, serverKeyStoreFile, serverKeyStorePass)

    // Save User KeyStore
    println("Saving User KeyStore to file")
    saveKeyStore(userKeyStore, userKeyStoreFile, userKeyStorePass)

    // Save HA KeyStore
    println("Saving HA KeyStore to file")
    saveKeyStore(haKeyStore, haKeyStoreFile, haKeyStorePass)

}

fun initCA() {
    val keyStore: KeyStore = KeyStore.getInstance("jks")
    keyStore.load(null, CA_KS_PASS.toCharArray())

    // Generate private key
    val keyPair: KeyPair = generateKeyPair()

    // Generate CSR
    val csr: PKCS10CertificationRequest =
        generateCSR("sec.ist.pt", "HDLT-CA", "SEC LLC", "Lisbon", "Lisbon", "PT", keyPair)

    // Generate self-signed certificate
    val certificate = signCSR(csr, keyPair.private)

    val array: Array<X509Certificate> = Array(1) { certificate }

    // Add key and certificate to keystore
    keyStore.setKeyEntry(CA_KEY_ALIAS, keyPair.private, CA_KEY_PASS.toCharArray(), array)

    saveKeyStore(keyStore, CA_KS_ALT_PATH, CA_KS_PASS)
}

fun generateKeyPair(): KeyPair {
    val keyGenerator: KeyPairGenerator = KeyPairGenerator.getInstance("RSA")
    keyGenerator.initialize(CA_KEY_SIZE)

    return keyGenerator.genKeyPair()
}

fun generateCSR(
    CN: String,
    OU: String,
    O: String,
    L: String,
    S: String,
    C: String,
    pair: KeyPair
): PKCS10CertificationRequest {
    val csrBuilder: PKCS10CertificationRequestBuilder =
        JcaPKCS10CertificationRequestBuilder(X500Principal("CN=$CN, OU=$OU, O=$O, L=$L, S=$S, C=$C"), pair.public)
    val csBuilder = JcaContentSignerBuilder("SHA256withRSA")

    return csrBuilder.build(csBuilder.build(pair.private))
}

fun signCSR(csr: PKCS10CertificationRequest, signing: PrivateKey): X509Certificate {
    val certBuilder = X509v3CertificateBuilder(
        X500Name("CN=sec.ist.pt"),
        BigInteger(159, SecureRandom()),
        Date(),
        Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365),
        csr.subject,
        csr.subjectPublicKeyInfo
    )
    val sigAlg: AlgorithmIdentifier = DefaultSignatureAlgorithmIdentifierFinder().find("SHA256withRSA")
    val digAlg: AlgorithmIdentifier = DefaultDigestAlgorithmIdentifierFinder().find(sigAlg)
    val key: AsymmetricKeyParameter = PrivateKeyFactory.createKey(signing.encoded)
    val signer: ContentSigner = BcRSAContentSignerBuilder(sigAlg, digAlg).build(key)

    val certFactory: CertificateFactory = try {
        CertificateFactory.getInstance("X.509", "BC")
    } catch (_: NoSuchProviderException) {
        // Add BouncyCastle if not present
        Security.addProvider(BouncyCastleProvider())
        CertificateFactory.getInstance("X.509", "BC")
    }

    val stream: InputStream = ByteArrayInputStream(
        certBuilder.build(signer)
            .toASN1Structure()
            .encoded
    )

    val certificate: X509Certificate = certFactory.generateCertificate(stream) as X509Certificate

    stream.close()

    return certificate
}

fun saveKeyStore(keystore: KeyStore, path: String, pass: String) {
    keystore.store(FileOutputStream(path), pass.toCharArray())
}

fun deriveKey(password: String, salt: String): String {
    val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt.toByteArray(), PBKDF2_ITER, PBKDF2_KEY_SIZE)
    val factory: SecretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")

    return Base64.getEncoder().encodeToString(factory.generateSecret(spec).encoded)
}