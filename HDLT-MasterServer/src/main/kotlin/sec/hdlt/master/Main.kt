package sec.hdlt.master

import io.grpc.ManagedChannel
import org.slf4j.LoggerFactory
import sec.hdlt.master.data.grid.GridCell
import sec.hdlt.protos.server.SetupGrpcKt
import sec.hdlt.protos.server.Server
import sec.hdlt.protos.master.HDLTMasterGrpcKt
import sec.hdlt.protos.master.Master
import tornadofx.launch
import java.io.Closeable
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Default values
 */
const val ROW_COUNT = 10
const val COL_COUNT = 10
const val SERVER_COUNT = 4
const val USER_COUNT = 5
const val EPOCH_INTERVAL = 0.5
const val F = 3
const val F_LINE = 2
const val BASE_SERVER_PORT = 7777
const val BYZANTINE_SERVERS = 1

fun main() {
    launch<MasterApplication>()
    exitProcess(0)
}

class SetupService(private val channel: ManagedChannel) : Closeable {
    private val logger = LoggerFactory.getLogger("SetupService")
    private val stub = SetupGrpcKt.SetupCoroutineStub(channel)

    suspend fun broadcastValues(f: Int, fLine: Int, serverCount: Int, byzantineServers: Int) {
        val request = Server.BroadcastValuesRequest.newBuilder().apply {
            this.f = f
            this.fLine = fLine
            this.serverCount = serverCount
            this.byzantineServers = byzantineServers
        }

        val response = stub.broadcastValues(request.build())
        logger.info("Received ${response.ok} from server")
    }

    override fun close() {
        channel.shutdown().awaitTermination(500, TimeUnit.MILLISECONDS)
    }
}

class MasterService(private val channel: ManagedChannel) : Closeable {
    private val logger = LoggerFactory.getLogger("MasterService")
    private val stub = HDLTMasterGrpcKt.HDLTMasterCoroutineStub(channel)

    suspend fun broadcastEpoch(epoch: Int, cells: Map<Int, GridCell>): Boolean {
        println("Broadcasting epoch $epoch with ${cells.size}cells")
        val request = Master.BroadcastEpochRequest.newBuilder().apply {
            this.epoch = epoch
            this.addAllCells(cells.map { (id, cell) ->
                createEpochCell(id, cell.x, cell.y)
            })
        }

        val response = stub.broadcastEpoch(request.build())
        logger.info("Received ok from user ${response.userId}")
        return response.ok
    }

    suspend fun sendInitSetup(numServers: Int, seed: Long, serverByzantine: Int) {
        val request = Master.InitRequest.newBuilder().apply {
            this.serverNum = numServers
            this.randomSeed = seed
            this.serverByzantine = serverByzantine
        }

        stub.init(request.build())
    }

    private fun createEpochCell(id: Int, x: Int, y: Int): Master.EpochCell? {
        return Master.EpochCell.newBuilder().apply {
            this.userId = id
            this.x = x
            this.y = y
        }.build()
    }

    override fun close() {
        channel.shutdown().awaitTermination(500, TimeUnit.MILLISECONDS)
    }
}