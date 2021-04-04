package sec.hdlt.master.views

import javafx.beans.binding.NumberExpressionBase
import javafx.scene.control.ButtonBar
import javafx.util.Duration
import sec.hdlt.master.*
import sec.hdlt.master.controllers.SimulatorController
import sec.hdlt.master.viewmodels.MasterSetupViewModel
import sec.hdlt.master.viewmodels.doubleOnly
import sec.hdlt.master.viewmodels.intOnly
import tornadofx.*

class MasterSetupView : View("MasterView | Setup") {
    private val simulatorController: SimulatorController by inject()

    private val model = MasterSetupViewModel()

    private fun ValidationContext.validate(field: String?, value: NumberExpressionBase, errorMsg: String): ValidationMessage? {
        if (field.isNullOrBlank() || value.lessThanOrEqualTo(0.0).value) {
            return error(errorMsg)
        }
        return null
    }

    override val root = borderpane {
        center = form {
            fieldset {
                field("Amount of users") {
                    textfield(model.userCount) { intOnly() }.validator {
                        validate(it, model.userCount, "The amount of users needs to be greater than 0")
                    }
                }
                field("Amount of columns") {
                    textfield(model.colCount) { intOnly() }.validator {
                        validate(it, model.colCount, "The amount of columns needs to be greater than 0")
                    }
                }
                field("Amount of rows") {
                    textfield(model.rowCount) { intOnly() }.validator {
                        validate(it, model.rowCount, "The amount of rows needs to be greater than 0")
                    }
                }
                field("Epoch interval") {
                    textfield(model.epochInterval) { doubleOnly() }.validator {
                        validate(it, model.epochInterval, "The interval needs to be positive")
                    }
                }
            }
        }

        bottom = buttonbar {
            button("Finish setup", type = ButtonBar.ButtonData.FINISH) {
                enableWhen(model.userCount.greaterThan(0))
                action {
                    simulatorController.setupSimulator(model)
                    replaceWith<MasterView>(
                        transition = ViewTransition.Slide(Duration(500.0)),
                        centerOnScreen = true,
                        sizeToScene = true
                    )
                }
            }
        }
    }

    override fun onDock() {
        model.userCount.value = USER_COUNT
        model.rowCount.value = ROW_COUNT
        model.colCount.value = COL_COUNT
        model.epochInterval.value = EPOCH_INTERVAL
    }
}
