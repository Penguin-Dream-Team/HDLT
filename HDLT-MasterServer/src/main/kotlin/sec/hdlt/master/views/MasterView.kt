package sec.hdlt.master.views

import sec.hdlt.master.controllers.SimulatorController
import tornadofx.*

object GridUpdateRequest : FXEvent(EventBus.RunOn.BackgroundThread)

class MasterView : View("MasterView") {

    private val simulatorController: SimulatorController by inject()

    override val root = borderpane {
        left = vbox {
            stackpane {

                button {
                    hiddenWhen(simulatorController.startedProperty)
                    text = "Start"
                    action {
                        runAsyncWithProgress {
                            isDisable = true
                            simulatorController.start()
                        }
                    }
                }
                button {
                    visibleWhen(simulatorController.startedProperty.and(simulatorController.pausedProperty))
                    text = "Resume"
                    action {
                        runAsyncWithProgress {
                            isDisable = true
                            simulatorController.resume()
                            isDisable = false
                        }
                    }
                }
                button {
                    visibleWhen(simulatorController.startedProperty.and(simulatorController.pausedProperty.not()))
                    text = "Pause"
                    action {
                        runAsyncWithProgress {
                            isDisable = true
                            simulatorController.pause()
                            isDisable = false
                        }
                    }
                }
            }
            button {
                visibleWhen(simulatorController.startedProperty.and(simulatorController.pausedProperty))
                text = "Step"
                action {
                    runAsyncWithProgress {
                        isDisable = true
                        simulatorController.step()
                        isDisable = false
                    }
                }
            }
        }

        center {
            add<GridView>()
        }
    }
}
