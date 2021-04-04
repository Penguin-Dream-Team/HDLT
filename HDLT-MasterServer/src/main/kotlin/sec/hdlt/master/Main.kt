package sec.hdlt.master

import io.grpc.ManagedChannel
import org.slf4j.LoggerFactory
import sec.hdlt.master.data.grid.GridCell
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
const val USER_COUNT = 10
const val EPOCH_INTERVAL = 0.5

fun main() {
    launch<MasterApplication>()
    exitProcess(0)
}

class MasterService(private val channel: ManagedChannel) : Closeable {
    private val logger = LoggerFactory.getLogger("MasterService")
    private val stub = HDLTMasterGrpcKt.HDLTMasterCoroutineStub(channel)

    suspend fun broadcastEpoch(epoch: Int, cells: Map<Int, GridCell>): Boolean {
        val request = Master.BroadcastEpochRequest.newBuilder().apply {
            this.epoch = epoch
            this.cellsBuilderList.addAll(cells.map { (id, cell) ->
                createEpochCell(id, cell.x, cell.y)
            })
        }

        val response = stub.broadcastEpoch(request.build())
        logger.info("Received ok from user ${response.userId}")
        return response.ok
    }

    private fun createEpochCell(id: Int, x: Int, y: Int): Master.EpochCell.Builder {
        return Master.EpochCell.newBuilder().apply {
            this.userId = id
            this.x = x
            this.y = y
        }
    }

    override fun close() {
        channel.shutdown().awaitTermination(500, TimeUnit.MILLISECONDS)
    }
}