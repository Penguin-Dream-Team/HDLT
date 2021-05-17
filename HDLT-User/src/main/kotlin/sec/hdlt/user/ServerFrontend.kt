package sec.hdlt.user

import io.grpc.ManagedChannelBuilder
import sec.hdlt.user.domain.Server
import sec.hdlt.user.dto.LocationRequest
import sec.hdlt.user.dto.LocationResponse
import sec.hdlt.user.dto.ReportDto
import sec.hdlt.user.services.CommunicationService
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

    /**
     * Submit a location report to the server
     */
    suspend fun submitReport(report: ReportDto): Boolean {
        return CommunicationService().submitReport(report, servers, quorum)
    }

    suspend fun getLocationReport(request: LocationRequest): Optional<LocationResponse> {
        return CommunicationService().getLocationReport(request, servers, quorum)
    }
}