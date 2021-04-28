package sec.hdlt.user

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sec.hdlt.protos.server.LocationGrpcKt
import sec.hdlt.protos.server.Report
import java.security.SignatureException

class ServerFrontend(host: String, port: Int, val num: Int) {
    private val channels: MutableList<ManagedChannel> = mutableListOf()

    init {
        var i = 0
        while (i < num) {
            channels.add(
                ManagedChannelBuilder.forAddress(host, port + i).usePlaintext()
                    .build()
            )

            i++
        }
    }

    suspend fun submitReport(request: Report.ReportRequest): List<Boolean> {
        val mutex = Mutex()
        val responses: MutableList<Boolean> = mutableListOf()

        coroutineScope {
            for (channel: ManagedChannel in channels) {
                launch {
                    val serverStub = LocationGrpcKt.LocationCoroutineStub(channel)

                    val response: Report.ReportResponse
                    try {
                        response = serverStub.submitLocationReport(request)
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

    suspend fun getLocationReport(request: Report.UserLocationReportRequest): List<Report.UserLocationReportResponse> {
        val mutex = Mutex()
        val responses: MutableList<Report.UserLocationReportResponse> = mutableListOf()

        coroutineScope {
            for (channel: ManagedChannel in channels) {
                launch {
                    val serverStub = LocationGrpcKt.LocationCoroutineStub(channel)

                    val response: Report.UserLocationReportResponse

                    try {
                        response = serverStub.getLocationReport(request)
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

                    mutex.withLock {
                        responses.add(response)
                    }

                }
            }
        }

        return responses
    }
}