package sec.hdlt.user.services

import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sec.hdlt.protos.server.LocationGrpcKt
import sec.hdlt.protos.server.Report
import sec.hdlt.user.*
import sec.hdlt.user.domain.Coordinates
import sec.hdlt.user.domain.Database
import sec.hdlt.user.domain.Server
import sec.hdlt.user.dto.*
import java.security.SignatureException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors
import javax.crypto.SecretKey

const val NO_REPORT = "NoReport"
const val END_COMM = "EndCommunication"
const val INVALID_REQ = "InvalidRequest"

val EMPTY_REPORT = LocationResponse(-1, -1, Coordinates(-1, -1), "", "", listOf())

const val LEADING_ZEROS = 5

object CommunicationService {

    suspend fun proofOfWork(message: String) {

    }

    suspend fun submitReport(report: ReportDto, servers: List<Server>, quorum: Int): Boolean {
        val channel = Channel<Unit>(Channel.CONFLATED)
        var okCount = 0
        var nokCount = 0
        val ackLock = Mutex()

        // Launch all coroutines in global scope (to avoid waiting for all)
        for (server: Server in servers) {
            GlobalScope.launch {
                val serverStub = LocationGrpcKt.LocationCoroutineStub(server.channel)
                val response: Report.ReportResponse

                val secret = generateKey()

                try {
                    response = serverStub.submitLocationReport(Report.ReportRequest.newBuilder().apply {
                        val messageNonce = generateNonce()
                        val serverCert = Database.getServerKey(server.id)

                        key =
                            asymmetricCipher(serverCert, Base64.getEncoder().encodeToString(secret.encoded))
                        nonce = Base64.getEncoder().encodeToString(messageNonce)
                        ciphertext = symmetricCipher(secret, Json.encodeToString(report), messageNonce)
                    }.build())

                    if (response.nonce.equals("") || response.ciphertext.equals("")) {
                        println("[SubmitReport] Empty response from server")
                        return@launch
                    }

                    val ack: Boolean = responseToAck(secret, response.nonce, response.ciphertext)

                    ackLock.withLock {
                        if (ack) {
                            okCount++

                            if (okCount > quorum) {
                                channel.offer(Unit)
                            }
                        } else {
                            nokCount++

                            if (nokCount > quorum) {
                                channel.offer(Unit)
                            }
                        }
                    }
                } catch (e: SignatureException) {
                    println("[SubmitReport] signature error $e")

                    ackLock.withLock {
                        nokCount++

                        if (nokCount > quorum) {
                            channel.offer(Unit)
                        }
                    }

                    return@launch
                } catch (e: StatusException) {
                    if (e.status.code == Status.INVALID_ARGUMENT.code) {
                        println("[SubmitReport] Invalid request detected")
                    } else {
                        println("[SubmitReport] gRPC error $e")
                    }

                    ackLock.withLock {
                        nokCount++

                        if (nokCount > quorum) {
                            channel.offer(Unit)
                        }
                    }

                    return@launch
                }
            }
        }

        channel.receive()

        val res: Boolean
        ackLock.withLock {
            res = nokCount < okCount
        }

        return res
    }

    suspend fun getLocationReport(
        request: LocationRequest,
        servers: List<Server>,
        quorum: Int
    ): Optional<LocationResponse> {
        val channel = Channel<Unit>(Channel.UNLIMITED)
        val responses = ConcurrentHashMap<Int, Optional<LocationResponse>>()
        var maxKey: LocationResponse = EMPTY_REPORT

        val mutex = Mutex()

        for (server: Server in servers) {
            GlobalScope.launch {
                val serverStub = LocationGrpcKt.LocationCoroutineStub(server.channel)

                val secret = generateKey()
                val serverCert = Database.getServerKey(server.id)

                try {
                    serverStub.getLocationReport(flow {
                        // Send report
                        emit(Report.UserLocationReportRequest.newBuilder().apply {
                            val messageNonce = generateNonce()

                            key = asymmetricCipher(serverCert, Base64.getEncoder().encodeToString(secret.encoded))
                            nonce = Base64.getEncoder().encodeToString(messageNonce)

                            ciphertext = symmetricCipher(secret, Json.encodeToString(request), messageNonce)
                        }.build())

                        // Wait for quorum of reports
                        channel.receive()

                        // Send termination message
                        emit(Report.UserLocationReportRequest.newBuilder().apply {
                            val messageNonce = generateNonce()
                            nonce = Base64.getEncoder().encodeToString(messageNonce)
                            ciphertext = symmetricCipher(secret, Json.encodeToString(END_COMM), messageNonce)
                        }.build())
                    }).collect { response ->
                        run {
                            if (response.nonce.equals("") || response.ciphertext.equals("")) {
                                println("[GetLocationReport] Empty response from server")
                            }

                            val deciphered = decipherResponse(secret, response.nonce, response.ciphertext)

                            try {
                                val message: String = Json.decodeFromString(deciphered)
                                // Check if report not found, invalid request or if end of communication

                                if (message == NO_REPORT) {
                                    println("[GetLocationReport] No report on the server")
                                } else if (message == INVALID_REQ) {
                                    println("[GetLocationReport] Invalid request")
                                } else if (message == END_COMM) {
                                    // Ignore
                                }
                            } catch (e: SerializationException) {
                                // Not a string, then it must be a report....

                                val report: LocationResponse = Json.decodeFromString(deciphered)
                                if (verifySignature(
                                        serverCert,
                                        "${request.id}${request.epoch}${report.coords}${report.serverInfo}${report.proofs.joinToString { "${it.prover}" }}",
                                        report.signature
                                    )
                                ) {
                                    // Check if overwriting previous report
                                    if (responses[server.id] == null || responses[server.id]!!.isEmpty) {
                                        responses[server.id] = Optional.of(report)

                                        mutex.withLock {
                                            // Group responses
                                            var curMax = -1
                                            var curKey: LocationResponse = EMPTY_REPORT
                                            responses.values.stream()
                                                .collect(Collectors.groupingByConcurrent { s -> s.orElse(EMPTY_REPORT) })
                                                .forEach { (k,v) -> if (v.size > curMax) {
                                                    curMax = v.size
                                                    curKey = k
                                                }}

                                            if (curMax > quorum) {
                                                maxKey = curKey
                                                // size + 1 due to non-flow wait
                                                for (i in 0..servers.size) {
                                                    channel.offer(Unit)
                                                }
                                            }
                                        }
                                    } else {
                                        println("[GetLocationReport] Server tried to overwrite")
                                    }
                                } else {
                                    println("[GetLocationReport] Response was not sent by server")
                                }
                            }
                        }
                    }
                } catch (e: SignatureException) {
                    println("Could not sign message")
                    return@launch
                } catch (e: StatusException) {
                    println("[GetLocationReport] Error connecting to server")
                    return@launch
                }
            }
        }

        channel.receive()

        return if (maxKey == EMPTY_REPORT) Optional.empty() else Optional.of(maxKey)
    }

    suspend fun getWitnessProofs(
        request: WitnessRequest,
        servers: MutableList<Server>,
        quorum: Int
    ) : Optional<WitnessResponse> {
        val channel = Channel<Unit>(Channel.UNLIMITED)
        val responses = ConcurrentHashMap<Int, Optional<LocationResponse>>()
        var maxKey: LocationResponse = EMPTY_REPORT

        val mutex = Mutex()

        for (server: Server in servers) {
            GlobalScope.launch {
                val serverStub = LocationGrpcKt.LocationCoroutineStub(server.channel)

                val secret = generateKey()
                val serverCert = Database.getServerKey(server.id)

                try {
                    // TODO READ REGULAR

                } catch (e: SignatureException) {
                    println("Could not sign message")
                    return@launch
                } catch (e: StatusException) {
                    println("[GetWitnessProofs] Error connecting to server")
                    return@launch
                }
            }
        }

        channel.receive()
    }
}

fun responseToAck(key: SecretKey, nonce: String, ciphertext: String): Boolean {
    return Json.decodeFromString(decipherResponse(key, nonce, ciphertext))
}

fun decipherResponse(key: SecretKey, nonce: String, ciphertext: String): String {
    val decodedNonce: ByteArray = Base64.getDecoder().decode(nonce)
    return symmetricDecipher(key, decodedNonce, ciphertext)
}