package sec.hdlt.user.service

import io.grpc.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sec.hdlt.protos.master.*
import sec.hdlt.protos.server.*
import sec.hdlt.protos.user.*
import sec.hdlt.user.*
import sec.hdlt.user.domain.*
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.SignatureException
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import kotlin.random.Random
import kotlin.streams.toList

class MasterService(private val serverChannel: ManagedChannel, private val mutex: Mutex = Mutex()) :
    HDLTMasterGrpcKt.HDLTMasterCoroutineImplBase() {

    override suspend fun broadcastEpoch(request: Master.BroadcastEpochRequest): Master.BroadcastEpochResponse {
        println("Receiving epoch ${request.epoch}")

        val board = Board()

        // Fill board
        request.cellsList.stream()
            .forEach { board.addUser(UserInfo(it.userId, Coordinates(it.x, it.y))) }

        val info = EpochInfo(board.getUserCoords(Database.id), request.epoch, board)

        mutex.withLock {
            Database.epochs.put(request.epoch, info)
        }

        GlobalScope.launch {
            // FIXME: activate this
            //delay(Random.nextLong(MIN_TIME_COM, MAX_TIME_COM) * 1000)

            communicate(info, serverChannel)
        }

        return Master.BroadcastEpochResponse.newBuilder().apply {
            userId = Database.id
            ok = true
        }.build()
    }
}

suspend fun communicate(info: EpochInfo, serverChannel: ManagedChannel) {
    // Byzantine Level 1: Skip communication
    if (Database.byzantineLevel >= 1 && Random.nextInt(100) < BYZ_PROB_NOT_SEND) {
        return
    }

    val users: List<UserInfo> = info.board.getAllUsers()

    // Byzantine Level 2: Tamper with fields
    // Create request once (will be equal for all gRPC calls)
    val request = User.LocationProofRequest.newBuilder().apply {
        id = if (Database.byzantineLevel >= 2 && Random.nextInt(100) < BYZ_PROB_TAMPER) {
            Random.nextInt(BYZ_MAX_ID_TAMPER)
        } else {
            Database.id
        }
        epoch = if (Database.byzantineLevel >= 2 && Random.nextInt(100) < BYZ_PROB_TAMPER) {
            Random.nextInt(BYZ_MAX_EP_TAMPER)
        } else {
            info.epoch
        }

        signature = if (Database.byzantineLevel >= 2 && Random.nextInt(100) < BYZ_PROB_TAMPER) {
            Base64.getEncoder().encodeToString(Random.nextBytes(BYZ_BYTES_TAMPER))
        } else {
            try {
                val sig: Signature = Signature.getInstance("SHA256withRSA")
                sig.initSign(Database.key)
                sig.update("${Database.id}${info.epoch}".toByteArray())
                Base64.getEncoder().encodeToString(sig.sign())
            } catch (e: SignatureException) {
                println("Couldn't sign message")
                return
            }
        }
    }.build()

    val mutex = Mutex()
    val proofs = mutableListOf<Proof>()

    // Launch call for each user
    coroutineScope {
        for (user: UserInfo in users) {
            launch {
                val userChannel: ManagedChannel =
                    ManagedChannelBuilder.forAddress("localhost", user.port).usePlaintext().build()
                val userStub = LocationProofGrpcKt.LocationProofCoroutineStub(userChannel)
                    .withDeadlineAfter(MAX_GRPC_TIME, TimeUnit.SECONDS)

                val response: User.LocationProofResponse

                try {
                    response = userStub.requestLocationProof(request)
                    userChannel.shutdownNow()
                } catch (e: StatusRuntimeException) {
                    when (e.status.code) {
                        Status.CANCELLED.code -> {
                            println("Responder detected invalid signature")
                        }
                        Status.FAILED_PRECONDITION.code -> {
                            //println("Responder thinks it is far")
                        }
                        Status.UNAUTHENTICATED.code -> {
                            println("Responder couldn't deliver message")
                        }
                        Status.INVALID_ARGUMENT.code -> {
                            println("Responder is in different epoch")
                        }
                        else -> println("Unknown error")
                    }

                    userChannel.shutdownNow()
                    return@launch
                } catch (e: StatusException) {
                    when (e.status.code) {
                        Status.CANCELLED.code -> {
                            println("Responder detected invalid signature")
                        }
                        Status.FAILED_PRECONDITION.code -> {
                            //println("Responder thinks it is far")
                        }
                        Status.UNAUTHENTICATED.code -> {
                            println("Responder couldn't deliver message")
                        }
                        Status.INVALID_ARGUMENT.code -> {
                            println("Responder is in different epoch")
                        }
                        else -> println("Unknown error")
                    }

                    userChannel.shutdownNow()
                    return@launch
                } catch (e: Exception) {
                    e.printStackTrace()
                    userChannel.shutdownNow()
                    return@launch
                }

                // Byzantine Level 5: No verification of data
                if (Database.byzantineLevel >= 5 && Random.nextInt(100) < BYZ_PROB_NO_VER) {
                    // Skip verifications
                } else {
                    // Check response
                    if (response.epoch == 0 && response.proverId == 0 && response.requesterId == 0 && response.signature.equals(
                            ""
                        )
                    ) {
                        return@launch
                    } else if (user.id != response.proverId || Database.id != response.requesterId) {
                        println("User ids do not match")
                        return@launch
                    } else if (info.epoch != response.epoch) {
                        println("User is in different epoch")
                        return@launch
                    } else if (!info.position.isNear(info.board.getUserCoords(user.id))) {
                        println("User is far")
                        return@launch
                    }

                    // Check signature
                    try {
                        val sig: Signature = Signature.getInstance("SHA256withRSA")
                        sig.initVerify(Database.keyStore.getCertificate(KEY_ALIAS_PREFIX + user.id))
                        sig.update("${Database.id}${user.id}${info.epoch}".toByteArray())
                        if (!sig.verify(Base64.getDecoder().decode(response.signature))) {
                            println("Invalid signature detected")
                            userChannel.shutdownNow()
                            return@launch
                        }
                    } catch (e: SignatureException) {
                        println("Invalid signature detected")
                        return@launch
                    } catch (e: IllegalArgumentException) {
                        println("Invalid base64 detected")
                        return@launch
                    }

                    mutex.withLock {
                        proofs.add(
                            Proof(
                                response.requesterId,
                                response.proverId,
                                response.epoch,
                                response.signature
                            )
                        )
                    }
                }
            } // launch coroutine
        } // for loop
    } // Coroutine scope

    if (proofs.size == 0) {
        return
    }

    // Byzantine Level 2: Tamper with fields
    val report: ReportInfo = if (Database.byzantineLevel >= 2 && Random.nextInt(100) < BYZ_PROB_TAMPER) {
        println("Tampering with request to server fields")
        var tampered = BYZ_MAX_TIMES_TAMPER

        ReportInfo(
            // Id
            if (tampered > 0 && Random.nextBoolean()) {
                tampered--
                Random.nextInt(BYZ_MAX_ID_TAMPER)
            } else {
                Database.id
            },

            // Epoch
            if (tampered > 0 && Random.nextBoolean()) {
                tampered--
                Random.nextInt(BYZ_MAX_EP_TAMPER)
            } else {
                info.epoch
            },

            // Location
            Coordinates(
                // X coord
                if (tampered > 0 && Random.nextBoolean()) {
                    tampered--
                    Random.nextInt(BYZ_MAX_COORDS_TAMPER)
                } else {
                    info.position.x
                },

                // Y coord
                if (tampered > 0 && Random.nextBoolean()) {
                    Random.nextInt(BYZ_MAX_COORDS_TAMPER)
                } else {
                    info.position.y
                }
            ),

            // Signature
            if (tampered > 0 && Random.nextBoolean()) {
                tampered--
                Base64.getEncoder().encodeToString(
                    Random.nextBytes(
                        BYZ_BYTES_TAMPER
                    )
                )
            } else {
                try {
                    val sig: Signature = Signature.getInstance("SHA256withRSA")
                    sig.initSign(Database.key)
                    sig.update("${Database.id}${info.epoch}${info.position}".toByteArray())
                    Base64.getEncoder().encodeToString(sig.sign())
                } catch (e: SignatureException) {
                    println("Couldn't sign message")
                    return
                }
            },

            // Location proofs
            proofs.stream().map {
                Proof(
                    // Requester Id
                    if (tampered > 0 && Random.nextBoolean()) {
                        tampered--
                        Random.nextInt(BYZ_MAX_ID_TAMPER)
                    } else {
                        it.requester
                    },

                    // Prover Id
                    if (tampered > 0 && Random.nextBoolean()) {
                        Random.nextInt(BYZ_MAX_ID_TAMPER)
                    } else {
                        it.prover
                    },

                    // Epoch
                    if (tampered > 0 && Random.nextBoolean()) {
                        Random.nextInt(BYZ_MAX_EP_TAMPER)
                    } else {
                        it.epoch
                    },

                    // Signature
                    if (tampered > 0 && Random.nextBoolean()) {
                        Base64.getEncoder().encodeToString(
                            Random.nextBytes(
                                BYZ_BYTES_TAMPER
                            )
                        )
                    } else {
                        it.signature
                    }
                )
            }.toList()
        )
    } else {
        // Non-byzantine request
        ReportInfo(
            Database.id,
            info.epoch,
            info.position,

            // Signature
            try {
                val sig: Signature = Signature.getInstance("SHA256withRSA")
                sig.initSign(Database.key)
                sig.update("${Database.id}${info.epoch}${info.position}".toByteArray())
                Base64.getEncoder().encodeToString(sig.sign())
            } catch (e: SignatureException) {
                println("Couldn't sign message")
                return
            },

            // Location Proofs
            proofs
        )
    }

    val serverRequest: Report.ReportRequest = Report.ReportRequest.newBuilder().apply {
        val secret = generateKey()
        val messageNonce = generateNonce()
        val serverCert = Database.serverCert
        key = asymmetricCipher(serverCert.publicKey, Base64.getEncoder().encodeToString(secret.encoded))
        nonce = Base64.getEncoder().encodeToString(messageNonce)
        ciphertext = symmetricCipher(secret, Json.encodeToString(report), messageNonce)
    }.build()

    // Send request to server
    val serverStub = LocationGrpcKt.LocationCoroutineStub(serverChannel)

    if (serverStub.locationReport(serverRequest).ack) {
        println("Request was OK")
    } else {
        println("BUSTED BY THE SERVER")
    }

    // Byzantine Level 0: Create non-existent request
    if (Database.byzantineLevel >= 0 && Random.nextInt(100) < BYZ_PROB_DUMB) {
        println("Forging location proof")

        val user = info.board.getRandomUser()

        val report = ReportInfo(
            // Id
            Database.id,

            // Epoch
            info.epoch,

            // Location
            user.coords,

            // Signature
            try {
                val sig: Signature = Signature.getInstance("SHA256withRSA")
                sig.initSign(Database.key)
                sig.update("${Database.id}${info.epoch}${user.coords}".toByteArray())
                Base64.getEncoder().encodeToString(sig.sign())
            } catch (e: SignatureException) {
                println("Couldn't sign message")
                return
            },

            mutableListOf(
                Proof(
                    // Requester
                    user.id,

                    // Prover
                    Database.id,

                    // Epoch
                    info.epoch,

                    // Signature
                    try {
                        val sig: Signature = Signature.getInstance("SHA256withRSA")
                        sig.initSign(Database.key)
                        sig.update("${user.id}${Database.id}${info.epoch}".toByteArray())
                        Base64.getEncoder().encodeToString(sig.sign())
                    } catch (e: SignatureException) {
                        println("Couldn't sign message")
                        return
                    }
                )
            )
        )

        if (serverStub.locationReport(Report.ReportRequest.newBuilder().apply {
                val secret = generateKey()
                val messageNonce = generateNonce()
                val serverCert = Database.serverCert
                key = asymmetricCipher(serverCert.publicKey, Base64.getEncoder().encodeToString(secret.encoded))
                nonce = Base64.getEncoder().encodeToString(messageNonce)
                ciphertext = symmetricCipher(secret, Json.encodeToString(report), messageNonce)
            }.build()).ack) {
            println("Request was OK :O")
        } else {
            println("Dumb user busted")
        }
    }
}

const val SYM_NONCE_LEN = 12
const val SYM_KEY_SIZE = 32

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

fun asymmetricCipher(key: PublicKey, plaintext: String): String {
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