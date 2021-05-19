package sec.hdlt.master.services

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sec.hdlt.master.data.Grid
import kotlin.math.roundToLong

class Simulator(
    rows: Int,
    cols: Int,
    private val serverCount: Int,
    private val userCount: Int,
    private val epochInterval: Double,
    private val f: Int,
    private val fLine: Int,
    private val serverByzantine: Int,
) {
    val grid: Grid = Grid(rows, cols, f, fLine)
    private val broadcaster: Broadcaster = Broadcaster(userCount)
    private var currentEpoch: Int = 0

    fun initAndLaunch() {
        broadcaster.initUsers(serverCount, serverByzantine)
        grid.initGrid(userCount)
        launch()
    }

    private val notifyChannel = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var paused = false

    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
        notifyChannel.offer(Unit)
    }

    fun step() {
        grid.printGrid()
        grid.stepGrid()
        onStep()

        broadcaster.broadcastEpoch(currentEpoch++, grid)
    }

    var onStep = {}
    private fun launch() {
        currentEpoch = 0
        GlobalScope.launch {
            while(true) {
                while (paused) {
                    notifyChannel.receive()
                }
                step()
                delay((epochInterval * 1000).roundToLong())
            }
        }
    }
}