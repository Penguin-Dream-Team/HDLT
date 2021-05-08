package sec.hdlt.server.services

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import sec.hdlt.protos.server2server.Server2Server
import sec.hdlt.protos.server2server.WriteGrpcKt
import sec.hdlt.server.BASE_PORT
import sec.hdlt.server.MAX_GRPC_TIME
import sec.hdlt.server.domain.LocationReport
import java.util.*
import java.util.concurrent.TimeUnit

class ServerToServerWriteService(server: Int, totalServers: Int) : WriteGrpcKt.WriteCoroutineImplBase() {
    private var serverId: Int = 0
    private var servers: Int = 0
    private var responses = mutableMapOf<Int, Boolean>()

    init {
        serverId = server
        servers = totalServers
    }

    suspend fun writeBroadCast(server: Int, timeStamp: Int, locationReport: LocationReport): Boolean {
        responses[timeStamp] = false

        val request = Server2Server.WriteBroadcastRequest.newBuilder().apply {
            serverId = server
            writtenTimestamp = timeStamp
            report = Server2Server.LocationReport.newBuilder().apply {
                key = ""
                nonce = ""
                ciphertext = ""
            }.build()

            /*signature = try {
                    sign(Database.key, "${Database.id}$epoch")
                } catch (e: SignatureException) {
                    println("[EPOCH $epoch] Couldn't sign message")
                    return
                }*/
        }.build()

        // Launch call for each server
        coroutineScope {
            for (server: Int in 0 until servers) {
                if (server == serverId) continue

                launch {
                    val serverChannel: ManagedChannel =
                        ManagedChannelBuilder.forAddress("localhost", BASE_PORT + server).usePlaintext().build()
                    val serverStub = WriteGrpcKt.WriteCoroutineStub(serverChannel)
                        .withDeadlineAfter(MAX_GRPC_TIME, TimeUnit.SECONDS)

                    val response: Server2Server.WriteBroadcastResponse

                    try {
                        response = serverStub.writeBroadcast(request)
                        if (CommunicationService.deliverAcks(
                            response.epoch,
                            response.serverId,
                            response.writtenTimestamp,
                            response.acknowledgment
                        )) updateResponse(timeStamp)
                        serverChannel.shutdownNow()
                    } catch (e: StatusException) {
                        when (e.status.code) {
                            // TODO STATUS
                            Status.UNAUTHENTICATED.code -> {
                                println("[SERVER $server] Prover couldn't deliver message")
                            }
                            Status.DEADLINE_EXCEEDED.code -> {
                                println("[SERVER $server] Server took too long to answer")
                            }
                            else -> {
                                println("[SERVER $server] ${e.message}"); }
                        }

                        serverChannel.shutdownNow()
                        return@launch
                    } catch (e: Exception) {
                        println("[SERVER $server] ${e.message}")
                        e.printStackTrace()
                        serverChannel.shutdownNow()
                        return@launch
                    }
                } // launch coroutine
            } // for loop
        } // Coroutine scope

        return responses[timeStamp]!!
    }

    private fun updateResponse(timeStamp: Int) {
        responses[timeStamp] = true
    }
}