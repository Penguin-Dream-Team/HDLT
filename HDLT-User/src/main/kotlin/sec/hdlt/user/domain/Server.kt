package sec.hdlt.user.domain

import io.grpc.ManagedChannel

data class Server(val host: String, val port: Int, val id: Int, val channel: ManagedChannel)