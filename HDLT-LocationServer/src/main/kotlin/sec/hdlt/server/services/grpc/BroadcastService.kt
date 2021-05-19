package sec.hdlt.server.services.grpc

import io.grpc.ManagedChannelBuilder
import io.grpc.StatusException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sec.hdlt.protos.server.BroadcastGrpcKt
import sec.hdlt.protos.server.Server
import sec.hdlt.server.*
import sec.hdlt.server.domain.Coordinates
import sec.hdlt.server.domain.Database
import sec.hdlt.server.domain.LocationReport
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors
import javax.crypto.SecretKey

const val BROADCAST_DELAY: Long = 30 * 1000

val BROADCAST_CHANNELS = ConcurrentHashMap<Int, ConcurrentHashMap<Int, Channel<Boolean>>>()
val BROADCAST_RESPONSES = ConcurrentHashMap<Int, ConcurrentHashMap<Int, MutableList<LocationReport>>>()
val BROADCAST_LOCK = Mutex()
val EMPTY_REPORT = LocationReport(-1, -1, Coordinates(-1, -1), "", listOf())

class BroadcastService : BroadcastGrpcKt.BroadcastCoroutineImplBase() {
    override suspend fun broadcast(request: Server.BroadcastRequest): Server.BroadcastResponse {
        val symKey: SecretKey = asymmetricDecipher(Database.key, request.key)
        val report: LocationReport = requestToLocationReport(symKey, request.nonce, request.ciphertext)

        BROADCAST_LOCK.withLock {
            val epochResponses: ConcurrentHashMap<Int, MutableList<LocationReport>> =
                if (BROADCAST_RESPONSES.containsKey(report.epoch)) {
                    BROADCAST_RESPONSES[report.epoch]!!
                } else {
                    ConcurrentHashMap<Int, MutableList<LocationReport>>()
                }

            val responses: MutableList<LocationReport>
            if (epochResponses.containsKey(report.epoch)) {
                responses = epochResponses[report.id]!!
                responses.add(report)
            } else {
                responses = mutableListOf(report)
            }

            // Group responses
            var curMax = -1
            val remaining = Database.numServers - responses.size
            responses.stream()
                .collect(Collectors.groupingByConcurrent { s -> s })
                .forEach { (_, v) ->
                    if (v.size > curMax) {
                        curMax = v.size
                    }
                }

            val epochListener: ConcurrentHashMap<Int, Channel<Boolean>>
            if (BROADCAST_CHANNELS.containsKey(report.epoch)) {
                epochListener = BROADCAST_CHANNELS[report.epoch]!!

                if (epochListener.containsKey(report.id)) {
                    val channel: Channel<Boolean> = epochListener[report.id]!!
                        if (curMax > Database.quorum) {
                            channel.offer(true)
                        } else if (remaining + curMax < Database.quorum) {
                            // Check if quorum is unreachable
                            channel.offer(false)
                        }
                }
            }
        }

        return Server.BroadcastResponse.getDefaultInstance()
    }
}

suspend fun broadcast(request: LocationReport): Boolean {
    // Broadcast request to all servers
    for (id in 0 until Database.numServers) {
        if (id != Database.id) {
            val stub = BroadcastGrpcKt.BroadcastCoroutineStub(
                ManagedChannelBuilder.forAddress("localhost", BASE_PORT + id).usePlaintext().build()
            )

            try {
                stub.broadcast(Server.BroadcastRequest.newBuilder().apply {
                    val secret = generateKey()
                    val messageNonce = generateNonce()

                    key = asymmetricCipher(
                        Database.keyStore.getCertificate(CERT_SERVER_PREFIX + id),
                        Base64.getEncoder().encodeToString(secret.encoded)
                    )
                    nonce = Base64.getEncoder().encodeToString(messageNonce)

                    ciphertext = symmetricCipher(secret, Json.encodeToString(request), messageNonce)
                }.build())
            } catch (e: StatusException) {
                println("[Broadcast] Error contacting server $id")
            }
        }
    }

    val channel = Channel<Boolean>(Channel.CONFLATED)

    BROADCAST_LOCK.withLock {
        val epochListener: ConcurrentHashMap<Int, Channel<Boolean>> =
            if (BROADCAST_CHANNELS.containsKey(request.epoch)) {
                BROADCAST_CHANNELS[request.epoch]!!
            } else {
                ConcurrentHashMap<Int, Channel<Boolean>>()
            }

        epochListener[request.id] = channel
    }

    // Activate timeout to obtain quorum
    val job = GlobalScope.launch {
        delay(BROADCAST_DELAY)
        channel.offer(false)
    }


    // Wait for response
    val response = channel.receive()

    // Cancel timeout if not triggered
    if (job.isActive) {
        job.cancel()
    }

    return response
}