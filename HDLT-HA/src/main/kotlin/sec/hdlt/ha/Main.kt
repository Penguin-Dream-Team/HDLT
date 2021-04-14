package sec.hdlt.ha

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sec.hdlt.ha.data.*
import sec.hdlt.protos.server.HAGrpcKt
import sec.hdlt.protos.server.Report
import java.io.IOException
import java.io.InputStream
import java.lang.NumberFormatException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.util.*
import javax.crypto.SecretKey

const val KEYSTORE_FILE = "/ha.jks"
const val KEYSTORE_PASS = "UL764S3C637P4SSW06D"
const val KEY_PASS = "123"
const val KEY_ALIAS_HA = "hdlt_ha"
const val KEY_ALIAS_SERVER = "hdlt_server"

suspend fun main(args: Array<String>) {
    println("*****************************")
    println("* Health Authorities Client *")
    println("*****************************")

    if (args.size != 2) {
        println("Usage: ha <server host> <server port>")
        return
    }

    val serverHost = args[0]
    val serverPort: Int
    try {
        serverPort = args[1].toInt()
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

    // Get server certificate
    val serverCert: Certificate = keyStore.getCertificate(KEY_ALIAS_SERVER)

    // Initialize connection to server
    val channel: ManagedChannel = ManagedChannelBuilder.forAddress(serverHost, serverPort).usePlaintext().build()
    val stub = HAGrpcKt.HACoroutineStub(channel)

    while (true) {
        println("1 - Request report of a user")
        println("2 - Request for one users at specific location")
        println("3 - Exit")
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
            println(">> ")
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

            try {
                val secret = generateKey()
                val response = stub.userLocationReport(Report.UserLocationReportRequest.newBuilder().apply {
                    val messageNonce = generateNonce()
                    key = asymmetricCipher(serverCert.publicKey, Base64.getEncoder().encodeToString(secret.encoded))
                    nonce = Base64.getEncoder().encodeToString(messageNonce)
                    ciphertext = symmetricCipher(secret, Json.encodeToString(ReportRequest(
                        user,
                        epoch,
                        sign(privKey, "${user}${epoch}")
                    )), messageNonce)
                }.build())

                if (response.nonce.equals("") || response.ciphertext.equals("")) {
                    println("No location found for epoch $epoch for user $user")
                    continue
                }

                val report: ReportResponse = responseToReport(secret, response.nonce, response.ciphertext)
                if (verifySignature(serverCert, "$user$epoch${report.coords}", report.signature)) {
                    println("User $user was at ${report.coords} in epoch $epoch")
                } else {
                    println("Response was not sent by server")
                }
            } catch (e: StatusException) {
                // FIXME: Handle
                e.printStackTrace()
                println("Couldn't contact server")
                continue
            }

        // Ask for users at given location and epoch
        } else if (option == 2) {
            println("Asking for users at given location and epoch")
            println("Insert <epoch> <coordinate x> <coordinate y>")
            println(">> ")
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

            try {
                val secret = generateKey()
                val response = stub.usersAtCoordinates(Report.UsersAtCoordinatesRequest.newBuilder().apply {
                    val messageNonce = generateNonce()
                    key = asymmetricCipher(serverCert.publicKey, Base64.getEncoder().encodeToString(secret.encoded))
                    nonce = Base64.getEncoder().encodeToString(messageNonce)
                    ciphertext = symmetricCipher(secret, Json.encodeToString(
                        LocationRequest(
                        coords,
                        epoch,
                        sign(privKey, "${coords}${epoch}")
                    )
                    ), messageNonce)
                }.build())

                if (response.nonce.equals("") || response.ciphertext.equals("")) {
                    println("No users found at coords $coords in epoch $epoch")
                    continue
                }

                val location: LocationResponse = responseToLocation(secret, response.nonce, response.ciphertext)
                if (verifySignature(serverCert, "$coords$epoch${location.users.joinToString { "$it," }}", location.signature)) {
                    println("Users found at ${location.coords} in epoch $epoch")
                    for (user in location.users) {
                        println(user)
                    }
                } else {
                    println("Response was not sent by server")
                }
            } catch (e: StatusException) {
                // FIXME: Handle
                e.printStackTrace()
                println("Couldn't contact server")
                continue
            }
        } else {
            println("Unknown option")
        }
    }
}

fun responseToReport(key: SecretKey, nonce: String, ciphertext: String): ReportResponse {
    val decodedNonce: ByteArray = Base64.getDecoder().decode(nonce)
    val deciphered = symmetricDecipher(key, decodedNonce, ciphertext)
    return Json.decodeFromString(deciphered)
}

fun responseToLocation(key: SecretKey, nonce: String, ciphertext: String): LocationResponse {
    val decodedNonce: ByteArray = Base64.getDecoder().decode(nonce)
    val deciphered = symmetricDecipher(key, decodedNonce, ciphertext)
    return Json.decodeFromString(deciphered)
}