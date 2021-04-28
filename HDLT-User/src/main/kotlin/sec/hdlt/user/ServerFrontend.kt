package sec.hdlt.user

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import sec.hdlt.user.domain.Database

class ServerFrontend(val num: Int, private val channels: MutableList<ManagedChannel> = mutableListOf()) {
    init {
        var i = 0
        while (i < num) {
            channels.add(
                ManagedChannelBuilder.forAddress(Database.serverHost, Database.serverPortBase + i).usePlaintext()
                    .build()
            )

            i++
        }
    }
}