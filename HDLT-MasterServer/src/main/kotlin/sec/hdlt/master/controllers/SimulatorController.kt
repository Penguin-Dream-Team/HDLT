package sec.hdlt.master.controllers

import javafx.beans.property.SimpleBooleanProperty
import sec.hdlt.master.services.Simulator
import sec.hdlt.master.viewmodels.MasterSetupViewModel
import sec.hdlt.master.views.GridUpdateRequest
import tornadofx.Controller
import tornadofx.getValue
import tornadofx.setValue

class SimulatorController : Controller() {

    val startedProperty = SimpleBooleanProperty(false)
    private var started by startedProperty
    val pausedProperty = SimpleBooleanProperty(false)
    private var paused by pausedProperty

    var simulator: Simulator? = null

    fun setupSimulator(model: MasterSetupViewModel) {
        simulator = Simulator(
            model.colCount.value,
            model.rowCount.value,
            model.userCount.value,
            model.epochInterval.value,
            model.f.value,
            model.fLine.value
        ).apply {
            onStep = {
                fire(GridUpdateRequest)
            }
        }
    }

    fun start() {
        simulator?.initAndLaunch()
        started = true
    }

    fun step(fLine: Int) {
        simulator?.step(fLine)
    }

    fun pause() {
        simulator?.pause()
        paused = true
    }

    fun resume() {
        simulator?.resume()
        paused = false
    }
}