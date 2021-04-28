package sec.hdlt.master.services

import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import sec.hdlt.master.MasterService
import sec.hdlt.master.data.Grid
import sec.hdlt.utils.HDLTRandom
import java.util.logging.Logger

class Broadcaster(userCount: Int) {

    companion object {
        private const val BASE_PORT = 8100
    }

    private val logger = Logger.getLogger("Broadcaster")

    init {
        GlobalScope.launch {
            users.map { (i, service) ->
                async {
                    try {
                        TODO(/*service.sendInitSetup(HDLTRandom.seed)*/)
                    } catch (e: Exception) {
                        logger.severe("Failed to send initialization parameters to user $i")
                    }
                }
            }.awaitAll()
        }
    }

    private val users = mutableMapOf<Int, MasterService>().apply {
        repeat(userCount) {
            put(
                it,
                MasterService(
                    ManagedChannelBuilder
                        .forAddress("localhost", BASE_PORT + it)
                        .usePlaintext()
                        .build()
                )
            )
        }
    }

    fun broadcastEpoch(epoch: Int, grid: Grid) {
        GlobalScope.launch {
            users.map { (i, service) ->
                async {
                    var ok = false
                    try {
                        ok = service.broadcastEpoch(epoch, grid.cells)
                    } catch (e: Exception) {
                        logger.severe("Failed to broadcast epoch to user $i")
                    }

                    if (!ok) {
                        logger.severe("Removing user $i")
                        users.remove(i)
                        grid.cells.remove(i) // remove from gridview
                    }
                }
            }.awaitAll()
        }
    }
}