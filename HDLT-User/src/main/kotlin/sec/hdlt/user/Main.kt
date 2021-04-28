package sec.hdlt.user

import io.grpc.ManagedChannelBuilder
import io.grpc.ServerBuilder
import io.grpc.StatusException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sec.hdlt.protos.server.LocationGrpcKt
import sec.hdlt.protos.server.Report
import sec.hdlt.user.domain.Database
import sec.hdlt.user.domain.LocationRequest
import sec.hdlt.user.domain.LocationResponse
import sec.hdlt.user.service.MasterService
import sec.hdlt.user.service.UserService
import java.io.IOException
import java.io.InputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SignatureException
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.spec.KeySpec
import java.util.*
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.random.Random

const val BASE_PORT: Int = 8100

// KeyStore relative params
const val KEYSTORE_FILE = "/keystore.jks"
const val KEYSTORE_PASS = "UL764S3C637P4SSW06D"
const val KEY_ALIAS_PREFIX = "hdlt_user_"
const val KEY_ALIAS_SERVER = "hdlt_server"

// Password relative params
const val PASS_SALT = "secret_salt"
const val PASS_PREFIX = "user_pass_id_"
const val PBKDF2_KEY_SIZE = 512
const val PBKDF2_ITER = 100001

// Communication relative params
const val MIN_TIME_COM = 0L // seconds
const val MAX_TIME_COM = 30L // seconds

const val MAX_GRPC_TIME = 60L // seconds

const val MAX_EPOCH_AHEAD = 10 // How many epochs can a user be ahead of another one

// Byzantine options
const val MIN_BYZ_LEV = -1 // Not byzantine
const val MAX_BYZ_LEV = 5 // Hardest byzantine
const val BYZ_PROB_DUMB = 30 // Probability of forging requests with same signature to server
const val BYZ_PROB_NOT_SEND = 20 // Probability of not communicating with near (and server consequently)
const val BYZ_PROB_TAMPER = 45 // Probability of tampering one of the fields in request to server
const val BYZ_PROB_REJ_REQ = 45 // Probability of rejecting another user's request
const val BYZ_PROB_PASS_REQ = 35 // Probability of redirecting request to other user
const val BYZ_PROB_NO_VER = 50 // Probability of not verifying information

const val BYZ_MAX_TIMES_TAMPER = 5
const val BYZ_MAX_ID_TAMPER = 100
const val BYZ_MAX_EP_TAMPER = 100
const val BYZ_MAX_COORDS_TAMPER = 100
const val BYZ_BYTES_TAMPER = 40

fun main(args: Array<String>) {
    val keystoreFile: InputStream = object {}.javaClass.getResourceAsStream(KEYSTORE_FILE)

    val keyStore = KeyStore.getInstance("jks")

    try {
        keyStore.load(keystoreFile, KEYSTORE_PASS.toCharArray())
    } catch (e: IOException) {
        println("Couldn't open KeyStore file")
        return
    } catch (e: CertificateException) {
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
    val privKey: PrivateKey =
        keyStore.getKey(KEY_ALIAS_PREFIX + id, deriveKey(PASS_PREFIX + id).toCharArray()) as PrivateKey

    // Get server key
    val serverCert: Certificate = keyStore.getCertificate(KEY_ALIAS_SERVER)

    // Initialize global DB
    Database(id, keyStore, privKey, serverCert, byzantine, mutableMapOf(), Random, 0)

    // Initialize server
    val server = ServerBuilder.forPort(listen)
        .addService(MasterService(channel))
        .addService(UserService())
        .build()

    // Allow server queries
    GlobalScope.launch {
        val stub = LocationGrpcKt.LocationCoroutineStub(channel)

        while (true) {
            println("Write request in the form `<epoch> [<user to get>]`")
            val request: List<String>

            try {
                request = readLine()!!.split(" ")
            } catch (e: NumberFormatException) {
                continue
            }

            if (request.size > 2 || request.isEmpty()) {
                println("Invalid syntax")
                continue
            }

            val epoch = request[0].toInt()

            try {
                val secret = generateKey()
                val response = stub.userLocationReport(Report.UserLocationReportRequest.newBuilder().apply {
                    val messageNonce = generateNonce()
                    key = asymmetricCipher(serverCert.publicKey, Base64.getEncoder().encodeToString(secret.encoded))
                    nonce = Base64.getEncoder().encodeToString(messageNonce)

                    ciphertext = symmetricCipher(
                        secret, Json.encodeToString(
                            LocationRequest(
                                // User Id
                                if (request.size == 2) {
                                    request[1].toInt()
                                } else {
                                    id
                                },

                                // Epoch
                                epoch,

                                // Signature
                                sign(privKey, "${id}${epoch}")
                            )
                        ),
                        messageNonce
                    )
                }.build())

                if (response.nonce.equals("") || response.ciphertext.equals("")) {
                    println("No location found for epoch $epoch")
                    continue
                }

                val report: LocationResponse = responseToLocation(secret, response.nonce, response.ciphertext)
                if (verifySignature(serverCert, "$id$epoch${report.coords}${report.serverInfo}${report.proofs.joinToString { "${it.prover}" }}", report.signature)) {
                    println("User $id was at ${report.coords} in epoch $epoch")
                    println("Server Info: ${report.serverInfo}")
                } else {
                    println("Response was not sent by server")
                }

            } catch (e: SignatureException) {
                println("Could not sign message")
                continue
            } catch (e: StatusException) {
                println("Error connecting to server")
                continue
            }
        }
    }

    server.start()
    server.awaitTermination()
}

fun deriveKey(password: String): String {
    val spec: KeySpec = PBEKeySpec(password.toCharArray(), PASS_SALT.toByteArray(), PBKDF2_ITER, PBKDF2_KEY_SIZE)
    val factory: SecretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")

    return Base64.getEncoder().encodeToString(factory.generateSecret(spec).encoded)
}

fun responseToLocation(key: SecretKey, nonce: String, ciphertext: String): LocationResponse {
    val decodedNonce: ByteArray = Base64.getDecoder().decode(nonce)
    val deciphered = symmetricDecipher(key, decodedNonce, ciphertext)
    return Json.decodeFromString(deciphered)
}