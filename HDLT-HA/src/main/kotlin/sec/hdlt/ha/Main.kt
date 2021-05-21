package sec.hdlt.ha

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.ServerBuilder
import io.grpc.StatusException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sec.hdlt.ha.data.*
import sec.hdlt.protos.master.HDLTMasterGrpcKt
import sec.hdlt.protos.master.Master
import sec.hdlt.protos.server.HAGrpcKt
import sec.hdlt.protos.server.Report
import java.io.IOException
import java.io.InputStream
import java.lang.NumberFormatException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SignatureException
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.util.*
import javax.crypto.SecretKey

const val KEYSTORE_FILE = "/ha.jks"
const val KEYSTORE_PASS = "KeyStoreHA"
const val KEY_PASS = "123"
const val KEY_ALIAS_HA = "hdlt_ha"
const val CERT_SERVER_PREFIX = "cert_hdlt_server_"
const val CERT_USER_PREFIX = "cert_hdlt_user_"
const val BASE_PORT = 9000

var locationHost: String = ""
var locationPort: Int = -1

fun main(args: Array<String>) {
    println("*****************************")
    println("* Health Authorities Client *")
    println("*****************************")

    if (args.size != 2) {
        println("Usage: ha <server host> <server port>")
        return
    }

    locationHost = args[0]
    try {
        locationPort = args[1].toInt()
    } catch (e: NumberFormatException) {
        println("Invalid port number")
        return
    }

    // Initialize KeyStore
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

    // Get private key
    val privKey: PrivateKey = keyStore.getKey(KEY_ALIAS_HA, KEY_PASS.toCharArray()) as PrivateKey

    Database.key = privKey
    Database.keyStore = keyStore

    GlobalScope.launch {
        while (true) {
            println("1 - Request report of a user")
            println("2 - Request for one users at specific location")
            println("3 - Request witness proofs of a user")
            println("4 - Exit")
            print(">> ")
            val option: Int

            try {
                option = readLine()!!.toInt()
            } catch (e: NumberFormatException) {
                println("Invalid option")
                continue
            }

            // Ask for report of one user
            if (option == 1) {
                println("Asking for report of a user")
                println("Insert <user id> <epoch>")
                print(">> ")
                val options: List<String> = readLine()!!.split(" ")

                if (options.size != 2) {
                    println("Usage: <user id> <epoch>")
                    continue
                }

                val user: Int
                val epoch: Int
                try {
                    user = options[0].toInt()
                    epoch = options[1].toInt()
                } catch (e: NumberFormatException) {
                    println("User id and Epoch need to be numbers")
                    continue
                }

                val report: Optional<ReportResponse>
                try {
                    report = Database.frontend.getLocationReport(ReportRequest(user, epoch, sign(Database.key, "$user$epoch")))
                } catch (e: SignatureException) {
                    println("Couldn't sign message")
                    continue
                }

                if (report.isEmpty) {
                    println("NO REPORT FOR USER $user EPOCH $epoch")
                } else {
                    println("GOT REPORT FOR USER $user EPOCH $epoch: ${report.get()}")
                }

                // Ask for users at given location and epoch
            } else if (option == 2) {
                println("Asking for users at given location and epoch")
                println("Insert <epoch> <coordinate x> <coordinate y>")
                print(">> ")
                val options: List<String> = readLine()!!.split(" ")

                if (options.size != 3) {
                    println("Usage: <epoch> <coordinate x> <coordinate y>")
                    continue
                }

                val epoch: Int
                val coords: Coordinates
                try {
                    epoch = options[0].toInt()
                    coords = Coordinates(options[1].toInt(), options[2].toInt())
                } catch (e: NumberFormatException) {
                    println("Epoch and both Coordinates need to be numbers")
                    continue
                }

                val users = Database.frontend.usersAtLocation(EpochLocationRequest(coords, epoch, sign(Database.key, "$coords$epoch")))

                println(if (users.isEmpty) "No users at $coords in epoch $epoch" else "Users found: ${users.get()}")

                // Ask for user witness proofs
            } else if (option == 3) {
                println("Asking for witness proofs of a user")
                println("Write request in the form `<user id> <epoch>+`")
                val request: List<String>

                try {
                    request = readLine()!!.split(" ")
                } catch (e: NumberFormatException) {
                    continue
                }

                if (request.size < 2) {
                    println("Invalid syntax")
                    continue
                }

                val user: Int
                val epochs: List<Int>
                try {
                    user = request[0].toInt()
                    epochs = request.subList(1, request.size).map { it.toInt() }
                } catch (e: NumberFormatException) {
                    continue
                }

                val proofs = Database.frontend.getWitnessProofs(
                    WitnessRequest(
                        // User Id
                        user,

                        // Epochs
                        epochs,

                        // Signature
                        sign(privKey, "${user}${epochs.joinToString { "$it" }}")
                    )
                )

                if (proofs.isEmpty) {
                    println("NO PROOFS AS WITNESS FOUND FOR USER $user")
                } else {
                    println("GOT USER $user PROOFS AS WITNESS:\n${proofs.get()}")
                }
            } else if (option == 4) {
                break
            } else {
                println("Unknown option")
            }
        }
    }

    val server = ServerBuilder.forPort(BASE_PORT)
        .addService(Setup())
        .build()

    server.start()
    server.awaitTermination()
}

fun responseToLocation(key: SecretKey, nonce: String, ciphertext: String): EpochLocationResponse {
    val decodedNonce: ByteArray = Base64.getDecoder().decode(nonce)
    val deciphered = symmetricDecipher(key, decodedNonce, ciphertext)
    return Json.decodeFromString(deciphered)
}

class Setup: HDLTMasterGrpcKt.HDLTMasterCoroutineImplBase() {
    override suspend fun init(request: Master.InitRequest): Master.InitResponse {
        // Initialize connection to server
        Database.initServer(locationHost, locationPort, request.serverNum, request.serverByzantine)

        return Master.InitResponse.getDefaultInstance()
    }
}