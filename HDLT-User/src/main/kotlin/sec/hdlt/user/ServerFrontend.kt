package sec.hdlt.user

import io.grpc.ManagedChannelBuilder
import io.grpc.StatusException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sec.hdlt.protos.server.LocationGrpcKt
import sec.hdlt.protos.server.Report
import sec.hdlt.user.domain.Database
import sec.hdlt.user.domain.Server
import sec.hdlt.user.dto.LocationRequest
import sec.hdlt.user.dto.LocationResponse
import sec.hdlt.user.dto.ReportDto
import java.security.SignatureException
import java.util.*

/**
 * Class to handle connections to multiple servers at once
 *
 * @param host the host where all the servers are (simplification: assume all servers are in the same host)
 * @param port the base port where the servers start listening
 * @param num the number of existing servers
 */
class ServerFrontend(host: String, port: Int, val num: Int) {
    private val servers: MutableList<Server> = mutableListOf()

    init {
        var i = 0
        while (i < num) {
            servers.add(
                Server(host, port + i, i, ManagedChannelBuilder.forAddress(host, port + i).usePlaintext().build())
            )
            i++
        }
    }

    /**
     * Submit a location report to the server
     */
    suspend fun submitReport(report: ReportDto): List<Boolean> {
        val mutex = Mutex()
        val responses: MutableList<Boolean> = mutableListOf()

        coroutineScope {
            for (server: Server in servers) {
                launch {
                    val serverStub = LocationGrpcKt.LocationCoroutineStub(server.channel)

                    val response: Report.ReportResponse
                    try {
                        response = serverStub.submitLocationReport(Report.ReportRequest.newBuilder().apply {
                            val secret = generateKey()
                            val messageNonce = generateNonce()
                            val serverCert = Database.getServerKey(server.id)

                            key = asymmetricCipher(serverCert, Base64.getEncoder().encodeToString(secret.encoded))
                            nonce = Base64.getEncoder().encodeToString(messageNonce)
                            ciphertext = symmetricCipher(secret, Json.encodeToString(report), messageNonce)
                        }.build())
                    } catch (e: StatusException) {
                        println("Server error when submitting report")
                        return@launch
                    }

                    mutex.withLock {
                        responses.add(response.ack)
                    }
                }
            }
        }

        return responses
    }

    suspend fun getLocationReport(request: LocationRequest): List<LocationResponse> {
        val mutex = Mutex()
        val responses: MutableList<LocationResponse> = mutableListOf()

        coroutineScope {
            for (server: Server in servers) {
                launch {
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
                    } catch (e: SignatureException) {
                        println("Could not sign message")
                        return@launch
                    } catch (e: StatusException) {
                        println("Error connecting to server")
                        return@launch
                    }

                    if (response.nonce.equals("") || response.ciphertext.equals("")) {
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
                            responses.add(report)
                        }
                    } else {
                        println("Response was not sent by server")
                    }
                }
            }
        }

        return responses
    }
}