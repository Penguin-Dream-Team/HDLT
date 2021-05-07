package sec.hdlt.server.services

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import sec.hdlt.protos.server2server.ReadGrpcKt
import sec.hdlt.protos.server2server.Server2Server
import sec.hdlt.server.BASE_PORT
import sec.hdlt.server.MAX_GRPC_TIME
import sec.hdlt.server.domain.LocationReport
import java.util.*
import java.util.concurrent.TimeUnit

class ServerToServerReadService(server: Int, totalServers: Int) : ReadGrpcKt.ReadCoroutineImplBase() {
    private var serverId: Int = 0
    private var servers: Int = 0

    init {
        serverId = server
        servers = totalServers
    }

    suspend fun readBroadCast(server: Int, read: Int) {
        val request = Server2Server.ReadBroadcastRequest.newBuilder().apply {
            serverId = server
            readId = read

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
                launch {
                    val serverChannel: ManagedChannel =
                        ManagedChannelBuilder.forAddress("localhost", BASE_PORT + server).usePlaintext().build()
                    val serverStub = ReadGrpcKt.ReadCoroutineStub(serverChannel)
                        .withDeadlineAfter(MAX_GRPC_TIME, TimeUnit.SECONDS)

                    val response: Server2Server.ReadBroadcastResponse

                    try {
                        response = serverStub.readBroadcast(request)
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
    }

    suspend fun readAcknowledgment(server: Int, read: Int, timeStamp: Int, locationReport: LocationReport) {
        val request = Server2Server.ReadAcknowledgmentRequest.newBuilder().apply {
            serverId = server
            readId = read
            maxTimeStamp = timeStamp
            readReport = Server2Server.LocationReport.newBuilder().apply {
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
                launch {
                    val serverChannel: ManagedChannel =
                        ManagedChannelBuilder.forAddress("localhost", BASE_PORT + server).usePlaintext().build()
                    val serverStub = ReadGrpcKt.ReadCoroutineStub(serverChannel)
                        .withDeadlineAfter(MAX_GRPC_TIME, TimeUnit.SECONDS)

                    val response: Server2Server.ReadAcknowledgmentResponse

                    try {
                        response = serverStub.readAcknowledgment(request)
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
    }

    suspend fun readReturn(locationReport: LocationReport?) {
        val request = Server2Server.ReadReturnRequest.newBuilder().apply {
            readValue = Server2Server.LocationReport.newBuilder().apply {
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
                launch {
                    val serverChannel: ManagedChannel =
                        ManagedChannelBuilder.forAddress("localhost", BASE_PORT + server).usePlaintext().build()
                    val serverStub = ReadGrpcKt.ReadCoroutineStub(serverChannel)
                        .withDeadlineAfter(MAX_GRPC_TIME, TimeUnit.SECONDS)

                    val response: Server2Server.ReadReturnResponse

                    try {
                        response = serverStub.readReturn(request)
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
    }
}