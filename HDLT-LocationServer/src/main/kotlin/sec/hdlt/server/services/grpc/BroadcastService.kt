package sec.hdlt.server.services.grpc

import io.grpc.Channel
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sec.hdlt.protos.server.BroadcastGrpcKt
import sec.hdlt.protos.server.Report
import sec.hdlt.protos.server.Server
import sec.hdlt.server.*
import sec.hdlt.server.domain.Database
import java.util.*

class BroadcastService: BroadcastGrpcKt.BroadcastCoroutineImplBase() {
    override suspend fun broadcast(request: Server.BroadcastRequest): Server.BroadcastResponse {
        return super.broadcast(request)
    }
}

suspend fun broadcast(request: Report.ReportRequest) {
    for (id in 0 until Database.numServers) {
        if (id != Database.id) {
            val stub = BroadcastGrpcKt.BroadcastCoroutineStub(ManagedChannelBuilder.forAddress("localhost", BASE_PORT + id).usePlaintext().build())

            stub.broadcast(Server.BroadcastRequest.newBuilder().apply {
                val secret = generateKey()
                val messageNonce = generateNonce()

                key = asymmetricCipher(Database.keyStore.getCertificate(CERT_SERVER_PREFIX+id), Base64.getEncoder().encodeToString(secret.encoded))
                nonce = Base64.getEncoder().encodeToString(messageNonce)

                ciphertext = symmetricCipher(secret, Json.encodeToString(request), messageNonce)
            }.build())
        }
    }
}