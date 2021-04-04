package sec.hdlt.user

import io.grpc.ManagedChannelBuilder
import io.grpc.ServerBuilder
import sec.hdlt.user.domain.Board
import sec.hdlt.user.domain.Coordinates
import sec.hdlt.user.domain.EpochInfo
import sec.hdlt.user.service.MasterService
import sec.hdlt.user.service.UserService
import java.io.IOException
import java.io.InputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.spec.KeySpec
import java.util.*
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

const val BASE_PORT: Int = 8100

// KeyStore relative params
const val KEYSTORE_FILE = "/keystore.jks"
const val KEYSTORE_PASS = "UL764S3C637P4SSW06D"
const val KEY_ALIAS_PREFIX = "hdlt_user_"

// Password relative params
const val PASS_SALT = "secret_salt"
const val PASS_PREFIX = "user_pass_id_"
const val PBKDF2_KEY_SIZE = 512
const val PBKDF2_ITER = 100001

// Communication relative params
const val MIN_TIME_COM = 5L // seconds
const val MAX_TIME_COM = 30L // seconds

// Byzantine options
const val MIN_BYZ_LEV = -1 // Not byzantine
const val MAX_BYZ_LEV =  7 // Hardest byzantine
const val BYZ_PROB_DUMB     = 50 // Probability of forging requests with same signature to server
const val BYZ_PROB_NOT_SEND = 50 // Probability of not communicating with server
const val BYZ_PROB_TAMPER   = 45 // Probability of tampering one of the fields in request to server
const val BYZ_PROB_REJ_REQ  = 45 // Probability of rejecting another user's request
const val BYZ_PROB_ALL_LOC  = 40 // Probability of stating he is near every user
const val BYZ_PROB_SKIP_COM = 35 // Probability of not requesting location proof to near users
const val BYZ_PROB_PASS_REQ = 35 // Probability of redirecting request to other user
const val BYZ_PROB_FAKE_LOC = 50 // Probability of faking location for this epoch
const val BYZ_PROB_NO_VER   = 50 // Probability of not verifying information

const val BYZ_MAX_ID_TAMPER     = 100
const val BYZ_MAX_EP_TAMPER     = 100
const val BYZ_MAX_COORDS_TAMPER = 100
const val BYZ_BYTES_TAMPER      = 40

fun main(args: Array<String>) {
    val keystoreFile: InputStream = object {}.javaClass.getResourceAsStream(KEYSTORE_FILE)

    val keyStore = KeyStore.getInstance("jks")

    try {
        keyStore.load(keystoreFile, KEYSTORE_PASS.toCharArray())
    } catch(e: IOException) {
        println("Couldn't open KeyStore file")
        return
    } catch(e: CertificateException) {
        println("Couldn't load all keys/certificates")
        return
    }

    if (args.size != 4) {
        println("Usage: user <id> <server host> <server port> <byzantine type>")
        return
    }

    val id = args[0].toInt()
    val locationHost = args[1]
    val locationPort: Int = args[2].toInt()
    val byzantine = args[3].toInt()

    if (byzantine < MIN_BYZ_LEV || byzantine > MAX_BYZ_LEV) {
        println("Unknown byzantine level")
        return
    }

    val listen = BASE_PORT + id

    // Initialize connection to Location Server
    val channel = ManagedChannelBuilder
        .forAddress(locationHost, locationPort)
        .usePlaintext()
        .build()

    // Get private key
    val privKey: PrivateKey = keyStore.getKey(KEY_ALIAS_PREFIX + id, deriveKey(PASS_PREFIX + id).toCharArray()) as PrivateKey

    val userInfo = EpochInfo(id, Coordinates(-1, -1), -1, Board(), privKey, keyStore, byzantine)

    // Initialize server
    val server = ServerBuilder.forPort(listen)
        .addService(MasterService(userInfo, channel))
        .addService(UserService(userInfo))
        .build()

    server.start()
    server.awaitTermination()
}

fun deriveKey(password: String): String {
    val spec: KeySpec = PBEKeySpec(password.toCharArray(), PASS_SALT.toByteArray(), PBKDF2_ITER, PBKDF2_KEY_SIZE)
    val factory: SecretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")

    return Base64.getEncoder().encodeToString(factory.generateSecret(spec).encoded)
}