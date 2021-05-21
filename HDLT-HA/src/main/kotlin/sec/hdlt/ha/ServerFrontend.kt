package sec.hdlt.ha

import io.grpc.ManagedChannelBuilder
import sec.hdlt.ha.data.*
import java.util.*

/**
 * Class to handle connections to multiple servers at once
 *
 * @param host the host where all the servers are (simplification: assume all servers are in the same host)
 * @param port the base port where the servers start listening
 * @param num the number of existing servers
 * @param quorum the number of replicas that need to send a correct response
 */
class ServerFrontend(host: String, port: Int, val num: Int, private val quorum: Int) {
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

    suspend fun getLocationReport(request: ReportRequest): Optional<ReportResponse> {
        return CommunicationService.getLocationReport(request, servers, quorum)
    }

    suspend fun usersAtLocation(request: EpochLocationRequest): Optional<EpochLocationResponse> {
        return CommunicationService.usersAtCoordinates(request, servers, quorum)
    }

    suspend fun getWitnessProofs(request: WitnessRequest): Optional<WitnessResponse> {
        return CommunicationService.getWitnessProofs(request, servers, quorum)
    }
}