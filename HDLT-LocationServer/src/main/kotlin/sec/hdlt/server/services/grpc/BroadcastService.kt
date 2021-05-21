package sec.hdlt.server.services.grpc

import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jooq.exception.DataAccessException
import sec.hdlt.protos.server.BroadcastGrpcKt
import sec.hdlt.protos.server.Server
import sec.hdlt.server.*
import sec.hdlt.server.domain.Coordinates
import sec.hdlt.server.domain.Database
import sec.hdlt.server.domain.LocationReport
import sec.hdlt.server.services.RequestValidationService
import java.lang.NullPointerException
import java.security.SignatureException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors
import javax.crypto.SecretKey

const val BROADCAST_DELAY: Long = 30 * 1000

val BROADCAST_CHANNELS = ConcurrentHashMap<Int, ConcurrentHashMap<Int, Channel<Optional<LocationReport>>>>()
val BROADCAST_RESPONSES = ConcurrentHashMap<Int, ConcurrentHashMap<Int, MutableList<BroadcastInfo>>>()
val BROADCAST_LOCK = Mutex()
val EMPTY_REPORT = LocationReport(-1, -1, Coordinates(-1, -1), "", listOf())

class BroadcastService : BroadcastGrpcKt.BroadcastCoroutineImplBase() {
    override suspend fun broadcast(request: Server.BroadcastRequest): Server.BroadcastResponse {
        val symKey: SecretKey = asymmetricDecipher(Database.key, request.key)
        val report: LocationReport = requestToLocationReport(symKey, request.nonce, request.ciphertext)

        // TODO: check nonce -> Create table, check if already exists

        val decipheredNonce = Base64.getDecoder().decode(request.nonce)
        try {
            Database.nonceDAO.storeServerNonce(decipheredNonce, request.serverId)
        } catch (e: DataAccessException) {
            println("[Broadcast] Received duplicate nonce in request")
            throw Status.INVALID_ARGUMENT.asException()
        }

        // Check request signature
        try {
            if (!verifySignature(Database.keyStore.getCertificate(CERT_SERVER_PREFIX + request.serverId), "${request.serverId}${request.nonce}", request.signature)) {
                println("[Broadcast] Received forged request")

               return Server.BroadcastResponse.getDefaultInstance()
            }
        } catch (e: SignatureException) {
            println("[Broadcast] Signature error $e")
        } catch (e: NullPointerException) {
            println("[Broadcast] Received forged request")
        }

        // Check report signature
        if (RequestValidationService.validateSignature(report.id, report.epoch, report.location, report.signature)) {
            BROADCAST_LOCK.withLock {
                val epochResponses: ConcurrentHashMap<Int, MutableList<BroadcastInfo>> =
                    if (BROADCAST_RESPONSES.containsKey(report.epoch)) {
                        BROADCAST_RESPONSES[report.epoch]!!
                    } else {
                        val map = ConcurrentHashMap<Int, MutableList<BroadcastInfo>>()
                        BROADCAST_RESPONSES[report.epoch] = map
                        map
                    }

                val responses: MutableList<BroadcastInfo>
                if (epochResponses.containsKey(report.id)) {
                    responses = epochResponses[report.id]!!

                    // Check if server already broadcasted
                    if (responses.stream().filter {
                        it.id == request.serverId
                        }.count() > 0) {
                            println("[Broadcast] Server broadcasted more than once")

                        return Server.BroadcastResponse.getDefaultInstance()
                    }

                    responses.add(BroadcastInfo(request.serverId, report))
                } else {
                    responses = mutableListOf(BroadcastInfo(request.serverId, report))
                    epochResponses[report.id] = responses
                }

                // Group responses
                var curMax = -1
                var curKey = EMPTY_REPORT
                val remaining = Database.numServers - responses.size
                responses.stream()
                    .collect(Collectors.groupingByConcurrent { s -> s.response })
                    .forEach { (k, v) ->
                        if (v.size > curMax) {
                            curMax = v.size
                            curKey = k
                        }
                    }

                val epochListener: ConcurrentHashMap<Int, Channel<Optional<LocationReport>>>
                if (BROADCAST_CHANNELS.containsKey(report.epoch)) {
                    epochListener = BROADCAST_CHANNELS[report.epoch]!!

                    if (responses.isNotEmpty() && epochListener.containsKey(report.id)) {
                        val channel: Channel<Optional<LocationReport>> = epochListener[report.id]!!
                        if (curMax > Database.quorum) {
                            channel.offer(Optional.of(curKey))
                        } else if (remaining + curMax < Database.quorum) {
                            // Check if quorum is unreachable
                            channel.offer(Optional.empty())
                        }
                    }
                }
            }
        }

        return Server.BroadcastResponse.getDefaultInstance()
    }
}

suspend fun broadcast(request: LocationReport): Optional<LocationReport> {
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
                    serverId = Database.id
                    signature = sign(Database.key, "$serverId$nonce")
                }.build())
            } catch (e: SignatureException) {
                println("[Broadcast] Error signing message $e")
            } catch (e: StatusException) {
                println("[Broadcast] Error contacting server $id")
            }
        }
    }

    val channel = Channel<Optional<LocationReport>>(Channel.CONFLATED)

    var res: Optional<LocationReport>? = null
    BROADCAST_LOCK.withLock {
        val epochListener: ConcurrentHashMap<Int, Channel<Optional<LocationReport>>> =
            if (BROADCAST_CHANNELS.containsKey(request.epoch)) {
                BROADCAST_CHANNELS[request.epoch]!!
            } else {
                val map = ConcurrentHashMap<Int, Channel<Optional<LocationReport>>>()
                BROADCAST_CHANNELS[request.epoch] = map
                map
            }

        epochListener[request.id] = channel

        val epochResponses: ConcurrentHashMap<Int, MutableList<BroadcastInfo>> =
            if (BROADCAST_RESPONSES.containsKey(request.epoch)) {
                BROADCAST_RESPONSES[request.epoch]!!
            } else {
                val map = ConcurrentHashMap<Int, MutableList<BroadcastInfo>>()
                BROADCAST_RESPONSES[request.epoch] = map
                map
            }

        val responses: MutableList<BroadcastInfo> = if (epochResponses.containsKey(request.id)) {
            epochResponses[request.id]!!
        } else {
            val list = mutableListOf<BroadcastInfo>()
            epochResponses[request.id] = list
            list
        }

        if (responses.isNotEmpty()) {
            // Group responses
            var curMax = -1
            var curKey = EMPTY_REPORT
            val remaining = Database.numServers - responses.size
            responses.stream()
                .collect(Collectors.groupingBy { s -> s })
                .forEach { (k, v) ->
                    if (v.size > curMax) {
                        curMax = v.size
                        curKey = k.response
                    }
                }

            if (curMax > Database.quorum) {
                res = Optional.of(curKey)
            } else if (remaining + curMax < Database.quorum) {
                // Check if quorum is unreachable
                res = Optional.empty()
            }
        }
    }

    if (res == null) {
        // Activate timeout to obtain quorum
        val job = GlobalScope.launch {
            delay(BROADCAST_DELAY)
            channel.offer(Optional.empty())
        }

        // Wait for response
        res = channel.receive()

        // Cancel timeout if not triggered
        if (job.isActive) {
            job.cancel()
        }
    }

    return res!!
}

data class BroadcastInfo(val id: Int, val response: LocationReport) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BroadcastInfo

        if (response != other.response) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + response.hashCode()
        return result
    }

}