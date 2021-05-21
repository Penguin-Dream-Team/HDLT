package sec.hdlt.ha

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
import sec.hdlt.ha.data.*
import sec.hdlt.protos.server.HAGrpcKt
import sec.hdlt.protos.server.Report
import java.security.SignatureException
import java.security.cert.Certificate
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors
import javax.crypto.SecretKey

const val NO_REPORT = "NoReport"
const val END_COMM = "EndCommunication"
const val INVALID_REQ = "InvalidRequest"

val EMPTY_REPORT = ReportResponse(-1, -1, Coordinates(-1, -1), "", "", listOf())
val EMPTY_LOCATION = EpochLocationResponse(mutableListOf(), Coordinates(-1, -1), -1, "")

object CommunicationService {

    suspend fun proofOfWork(message: String) {

    }

    suspend fun getLocationReport(
        request: ReportRequest,
        servers: List<Server>,
        quorum: Int
    ): Optional<ReportResponse> {
        val channel = Channel<Unit>(Channel.UNLIMITED)
        val responses = ConcurrentHashMap<Int, Optional<ReportResponse>>()
        var maxKey: ReportResponse = EMPTY_REPORT

        val mutex = Mutex()

        for (server: Server in servers) {
            GlobalScope.launch {
                val serverStub = HAGrpcKt.HACoroutineStub(server.channel)

                val secret = generateKey()
                val serverCert = Database.getServerKey(server.id)

                try {
                    serverStub.userLocationReport(flow {
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
                                mutex.withLock {
                                    responses[server.id] = Optional.of(EMPTY_REPORT)

                                    // Group responses
                                    var curMax = -1
                                    var curKey: ReportResponse = EMPTY_REPORT
                                    responses.values.stream()
                                        .collect(Collectors.groupingByConcurrent { s ->
                                            (s.orElse(
                                                EMPTY_REPORT
                                            )).toString()
                                        })
                                        .forEach { (_, v) ->
                                            if (v.size > curMax) {
                                                curMax = v.size
                                                curKey = v[0].get()
                                            }
                                        }

                                    if (curMax > quorum) {
                                        maxKey = curKey
                                        // size + 1 due to non-flow wait
                                        for (i in 0..servers.size) {
                                            channel.offer(Unit)
                                        }
                                    }
                                }
                            }

                            val deciphered = decipherResponse(secret, response.nonce, response.ciphertext)

                            try {
                                val message: String = Json.decodeFromString(deciphered)
                                // Check if report not found, invalid request or if end of communication

                                if (message == NO_REPORT) {
                                    println("[GetLocationReport] No report on the server")
                                    mutex.withLock {
                                        responses[server.id] = Optional.of(EMPTY_REPORT)

                                        // Group responses
                                        var curMax = -1
                                        var curKey: ReportResponse = EMPTY_REPORT
                                        responses.values.stream()
                                            .collect(Collectors.groupingByConcurrent { s ->
                                                (s.orElse(
                                                    EMPTY_REPORT
                                                )).toString()
                                            })
                                            .forEach { (_, v) ->
                                                if (v.size > curMax) {
                                                    curMax = v.size
                                                    curKey = v[0].get()
                                                }
                                            }

                                        if (curMax > quorum) {
                                            maxKey = curKey
                                            // size + 1 due to non-flow wait
                                            for (i in 0..servers.size) {
                                                channel.offer(Unit)
                                            }
                                        }
                                    }
                                } else if (message == INVALID_REQ) {
                                    println("[GetLocationReport] Invalid request")
                                    mutex.withLock {
                                        responses[server.id] = Optional.of(EMPTY_REPORT)

                                        // Group responses
                                        var curMax = -1
                                        var curKey: ReportResponse = EMPTY_REPORT
                                        responses.values.stream()
                                            .collect(Collectors.groupingByConcurrent { s ->
                                                (s.orElse(
                                                    EMPTY_REPORT
                                                )).toString()
                                            })
                                            .forEach { (_, v) ->
                                                if (v.size > curMax) {
                                                    curMax = v.size
                                                    curKey = v[0].get()
                                                }
                                            }

                                        if (curMax > quorum) {
                                            maxKey = curKey
                                            // size + 1 due to non-flow wait
                                            for (i in 0..servers.size) {
                                                channel.offer(Unit)
                                            }
                                        }
                                    }
                                } else if (message == END_COMM) {
                                    // Ignore
                                }
                            } catch (e: SerializationException) {
                                // Not a string, then it must be a report....

                                val report: ReportResponse = Json.decodeFromString(deciphered)
                                if (report.epoch == request.epoch && report.id == request.id) {
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
                                                var curKey: ReportResponse = EMPTY_REPORT
                                                responses.values.stream()
                                                    .collect(Collectors.groupingByConcurrent { s ->
                                                        (s.orElse(
                                                            EMPTY_REPORT
                                                        )).toString()
                                                    })
                                                    .forEach { (_, v) ->
                                                        if (v.size > curMax) {
                                                            curMax = v.size
                                                            curKey = v[0].get()
                                                        }
                                                    }

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
                                } else {
                                    println("[GetLocationReport] Report not for this user/epoch")
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

    suspend fun usersAtCoordinates(
        request: EpochLocationRequest,
        servers: List<Server>,
        quorum: Int
    ): Optional<EpochLocationResponse> {
        val channel = Channel<Unit>(Channel.CONFLATED)
        val result = mutableListOf<sec.hdlt.ha.data.Report>()
        var numResponses = 0
        val mutex = Mutex()

        for (server: Server in servers) {
            GlobalScope.launch {
                val serverStub = HAGrpcKt.HACoroutineStub(server.channel)

                val secret = generateKey()
                val serverCert = Database.getServerKey(server.id)

                try {
                    val response = serverStub.usersAtCoordinates(Report.UsersAtCoordinatesRequest.newBuilder().apply {
                        val messageNonce = generateNonce()

                        key =
                            asymmetricCipher(serverCert, Base64.getEncoder().encodeToString(secret.encoded))
                        nonce = Base64.getEncoder().encodeToString(messageNonce)
                        ciphertext = symmetricCipher(secret, Json.encodeToString(request), messageNonce)
                    }.build())

                    if (response.nonce.equals("") || response.ciphertext.equals("")) {
                        println("[UsersAtCoordinates] Empty response from server")
                        return@launch
                    }

                    val locationInfo = responseToEpochLocation(secret, response.nonce, response.ciphertext)

                    if (verifySignature(
                            serverCert,
                            "$locationInfo.coordinates$locationInfo.epoch${locationInfo.users.joinToString { "$it" }}",
                            request.signature
                        )
                    ) {
                        if (locationInfo.coords == request.coords && locationInfo.epoch == request.epoch) {
                            val valid = locationInfo.users.stream().filter {
                                verifyEpochLocationReport(request.epoch, request.coords, it)
                            }

                            mutex.withLock {
                                valid.forEach {
                                    if (!result.contains(it)) {
                                        result.add(it)
                                    }
                                }
                            }

                        } else {
                            println("[UsersAtLocation] Information not for this coordinates/epoch")
                        }
                    } else {
                        println("[UsersAtLocation] Response was not sent by server")
                    }

                    mutex.withLock {
                        if (++numResponses == quorum) {
                            channel.offer(Unit)
                        }
                    }
                } catch (e: SignatureException) {
                    println("Could not sign message")
                    return@launch
                } catch (e: StatusException) {
                    println("[UsersAtCoordinates] Error connecting to server")
                    return@launch
                }
            }
        }

        channel.receive()

        return if (result.isEmpty()) Optional.empty() else Optional.of(
            EpochLocationResponse(
                result,
                request.coords,
                request.epoch,
                ""
            )
        )
    }
}

fun decipherResponse(key: SecretKey, nonce: String, ciphertext: String): String {
    val decodedNonce: ByteArray = Base64.getDecoder().decode(nonce)
    return symmetricDecipher(key, decodedNonce, ciphertext)
}

fun responseToEpochLocation(key: SecretKey, nonce: String, ciphertext: String): EpochLocationResponse {
    val decodedNonce: ByteArray = Base64.getDecoder().decode(nonce)
    return Json.decodeFromString(symmetricDecipher(key, decodedNonce, ciphertext))
}

fun verifyEpochLocationReport(epoch: Int, coords: Coordinates, report: sec.hdlt.ha.data.Report): Boolean {
    return report.epoch == epoch &&
            report.location == coords &&
            report.proofs.isNotEmpty() &&
            report.proofs.stream()
                .anyMatch { verifyProof(report.id, it) }
}

fun verifyProof(user: Int, proof: Proof): Boolean {
    return try {
        val cert: Certificate = Database.keyStore.getCertificate(CERT_USER_PREFIX + user)
        verifySignature(cert, "$user${proof.prover}${proof.epoch}", proof.signature)
    } catch (e: SignatureException) {
        false
    } catch (e: NullPointerException) {
        false
    }
}