package sec.hdlt.user.services

import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sec.hdlt.protos.server.LocationGrpcKt
import sec.hdlt.protos.server.Report
import sec.hdlt.user.*
import sec.hdlt.user.domain.Database
import sec.hdlt.user.domain.Server
import sec.hdlt.user.dto.LocationRequest
import sec.hdlt.user.dto.LocationResponse
import sec.hdlt.user.dto.ReportDto
import java.security.SignatureException
import java.util.*
import javax.crypto.SecretKey

class CommunicationService {
    companion object {
        private var servers = 0


        // Common process values
        //private var timestampValue = Pair<Int, ReportDto?>(0, null)
        private var readList = mutableListOf<Pair<Int, ReportDto?>>()
        private var readId = 0

        // Writer process values
        private var writtenTimestamp = 0
        private var acknowledgments = mutableMapOf<Int, Int>()

        // Reader process values
        //private var readValue: ReportDto? = null
        private var reading: Boolean = false

        fun initValues(numberOfServers: Int) {
            servers = numberOfServers
        }

        // ------------------------------ Read Operations ------------------------------
        suspend fun read(userId: Int, epoch: Int) {
            println("[EPOCH $epoch] new read from user $userId")

            readId++
            acknowledgments[writtenTimestamp] = 1
            readList.clear()
            reading = true
        }

        suspend fun deliverValue(serverId: Int, report: LocationResponse): Boolean {
            println("[EPOCH ${report.epoch}] Received a value from server $serverId")

            //readList[serverId] = Pair(writtenTimestamp, reportDto)
            if (readList.size > servers / 2) {
                var maxPair = readList[0]
                readList.forEach { pair ->
                    if (pair.first > maxPair.first) maxPair = pair
                }
                readList.clear()

                println("[EPOCH ${report.epoch}] Received all values. Writing-Back report ...")
                //writeService.writeBroadCast(readId, maxPair.first, maxPair.second!!)
                return true
            }
            return false
        }
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

    suspend fun getLocationReport(request: LocationRequest, servers: List<Server>, quorum: Int): LocationResponse {
        val channel = Channel<Unit>(Channel.CONFLATED)
        val responses = mutableMapOf<Int, LocationResponse>()

        val mutex = Mutex()

        for (server: Server in servers) {
            GlobalScope.launch {
                val serverStub = LocationGrpcKt.LocationCoroutineStub(server.channel)
                val response: Report.UserLocationReportResponse

                val secret = generateKey()
                val serverCert = Database.getServerKey(server.id)

                try {
                    response = serverStub.getLocationReport(Report.UserLocationReportRequest.newBuilder().apply {
                        val messageNonce = generateNonce()

                        key = asymmetricCipher(serverCert, Base64.getEncoder().encodeToString(secret.encoded))
                        nonce = Base64.getEncoder().encodeToString(messageNonce)

                        ciphertext = symmetricCipher(secret, Json.encodeToString(request), messageNonce)
                    }.build())

                    if (response.nonce.equals("") || response.ciphertext.equals("")) {
                        println("[GetLocationReport] Empty response from server")
                        return@launch
                    }

                    val report: LocationResponse = responseToLocation(secret, response.nonce, response.ciphertext)
                    if (verifySignature(
                            serverCert,
                            "${request.id}${request.epoch}${report.coords}${report.serverInfo}${report.proofs.joinToString { "${it.prover}" }}",
                            report.signature
                        )
                    ) {
                        mutex.withLock {
                            responses[server.id] = report
                        }
                    } else {
                        println("[] Response was not sent by server")
                    }
                } catch (e: SignatureException) {
                    println("Could not sign message")
                    return@launch
                } catch (e: StatusException) {
                    println("Error connecting to server")
                    return@launch
                }



            }
        }

        channel.receive()

        return responses[0]!!
    }
}

fun responseToLocation(key: SecretKey, nonce: String, ciphertext: String): LocationResponse {
    return Json.decodeFromString(decipherResponse(key, nonce, ciphertext))
}

fun responseToAck(key: SecretKey, nonce: String, ciphertext: String): Boolean {
    return Json.decodeFromString(decipherResponse(key, nonce, ciphertext))
}


fun decipherResponse(key: SecretKey, nonce: String, ciphertext: String): String {
    val decodedNonce: ByteArray = Base64.getDecoder().decode(nonce)
    return symmetricDecipher(key, decodedNonce, ciphertext)
}