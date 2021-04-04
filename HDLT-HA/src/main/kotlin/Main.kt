import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import sec.hdlt.master.protos.GreeterGrpcKt
import sec.hdlt.master.protos.Test
import java.io.Closeable
import java.util.concurrent.TimeUnit

suspend fun main() {
    val port = 7777

    val channel = ManagedChannelBuilder
        .forAddress("localhost", port)
        .usePlaintext()
        .build()
    val client = MasterClient(channel)
    GlobalScope.launch {
        client.greet("GREEETINGS!!")
    }
    client.greetStream("GREEETINGS!!")
}

class MasterClient(private val channel: ManagedChannel) : Closeable {
    private val stub = GreeterGrpcKt.GreeterCoroutineStub(channel)

    suspend fun greetStream(message: String) {
        val request = Test.GreetRequest.newBuilder().apply {
            this.message = message
        }.build()
        val stream = stub.greetStream(request)
        stream.withIndex().map { (id, it) -> id to it.message }.collect { (id, msg) ->
            println("$id: $msg")
        }
    }

    suspend fun greet(message: String) {
        val request = Test.GreetRequest.newBuilder().apply {
            this.message = message
        }.build()
        val response = stub.greet(request)
        println(response.message)
    }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}
