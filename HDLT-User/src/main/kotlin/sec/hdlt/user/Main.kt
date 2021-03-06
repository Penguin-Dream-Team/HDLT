package sec.hdlt.user

import io.grpc.ServerBuilder
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import sec.hdlt.user.domain.Database
import sec.hdlt.user.dto.LocationRequest
import sec.hdlt.user.dto.WitnessRequest
import sec.hdlt.user.services.MasterService
import sec.hdlt.user.services.UserService
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
const val KEYSTORE_FILE = "/user.jks"
const val KEYSTORE_PASS = "UL764S3C637P4SSW06D"
const val KEY_ALIAS_PREFIX = "hdlt_user_"
const val CERT_SERVER_PREFIX = "cert_hdlt_server_"

// Password relative params
const val PASS_SALT = "secret_salt"
const val PASS_PREFIX = "user_pass_id_"
const val PBKDF2_KEY_SIZE = 512
const val PBKDF2_ITER = 100001

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

var locationHost: String = ""
var locationPort: Int = 0

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
    locationHost = args[1]
    locationPort = args[2].toInt()
    val byzantine = args[3].toInt()

    if (byzantine < MIN_BYZ_LEV || byzantine > MAX_BYZ_LEV) {
        println("Unknown byzantine level")
        return
    }

    val listen = BASE_PORT + id

    // Get private key
    val privKey: PrivateKey =
        keyStore.getKey(KEY_ALIAS_PREFIX + id, deriveKey(PASS_PREFIX + id).toCharArray()) as PrivateKey

    // Initialize global DB
    Database.id = id
    Database.keyStore = keyStore
    Database.key = privKey
    Database.byzantineLevel = byzantine

    // Initialize server
    val server = ServerBuilder.forPort(listen)
        .addService(MasterService())
        .addService(UserService())
        .build()

    // Allow server queries
    GlobalScope.launch {
        while (true) {
            println("Write 0 to request a report or write 1 to request your proofs as witness")
            val type: List<String>

            try {
                type = readLine()!!.split(" ")
            } catch (e: NumberFormatException) {
                continue
            }

            if (type.size > 1 || type.isEmpty()) {
                println("Invalid syntax")
                continue
            }

            if (type[0].toInt() == 0) {
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

                val report = Database.frontend.getLocationReport(
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
                )

                if (report.isEmpty) {
                    println("NO REPORT FOR EPOCH $epoch")
                } else {
                    println("GOT REPORT FOR EPOCH $epoch: ${report.get()}")
                }
            }
            else if (type[0].toInt() == 1) {
                println("Write request in the form `<epoch>+`")
                val request: List<String>

                try {
                    request = readLine()!!.split(" ")
                } catch (e: NumberFormatException) {
                    continue
                }

                if (request.isEmpty()) {
                    println("Invalid syntax")
                    continue
                }

                val epochs: List<Int>
                try {
                    epochs = request.map { it.toInt() }
                } catch (e: NumberFormatException) {
                    continue
                }

                val proofs = Database.frontend.getWitnessProofs(
                    WitnessRequest(
                        // User Id
                        id,

                        // Epochs
                        epochs,

                        // Signature
                        sign(privKey, "${id}${epochs.joinToString { "$it" }}")
                    )
                )

                if (proofs.isEmpty) {
                    println("NO PROOFS AS WITNESS FOUND FOR YOUR EPOCHS")
                } else {
                    println("GOT PROOFS AS WITNESS:\n${proofs.get()}")
                }
            }
            else {
                println("Unknown command")
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