package sec.hdlt.master.views

import io.grpc.ManagedChannelBuilder
import javafx.beans.binding.NumberExpressionBase
import javafx.beans.property.SimpleIntegerProperty
import javafx.scene.control.ButtonBar
import javafx.util.Duration
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import sec.hdlt.master.*
import sec.hdlt.master.controllers.SimulatorController
import sec.hdlt.master.viewmodels.MasterSetupViewModel
import sec.hdlt.master.viewmodels.doubleOnly
import sec.hdlt.master.viewmodels.intOnly
import sec.hdlt.master.viewmodels.longOnly
import sec.hdlt.utils.HDLTRandom
import tornadofx.*
import java.util.logging.Logger
import kotlin.random.Random

class MasterSetupView : View("MasterView | Setup") {
    private val simulatorController: SimulatorController by inject()

    private val model = MasterSetupViewModel()

    private val logger = Logger.getLogger("MasterSetupView")

    private fun ValidationContext.validate(
        field: String?,
        value: NumberExpressionBase,
        errorMsg: String
    ): ValidationMessage? {
        if (field.isNullOrBlank() || value.lessThanOrEqualTo(0.0).value) {
            return error(errorMsg)
        }
        return null
    }

    private fun ValidationContext.validateF(
        field: String?,
        value: SimpleIntegerProperty,
        maxValue: SimpleIntegerProperty,
        name: String
    ): ValidationMessage? {
        return if (field.isNullOrBlank() || value < 0) {
            error("$name cannot be negative")
        } else if (value >= maxValue && value.value != 0) {
            error("$name has to be less than F")
        } else {
            null
        }
    }

    override val root = borderpane {
        center = form {
            fieldset {
                field("Amount of servers") {
                    textfield(model.serverCount) { intOnly() }.validator {
                        validate(it, model.serverCount, "The amount of servers needs to be greater than 0")
                    }
                }
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
                field("F") {
                    textfield(model.f) { intOnly() }.validator {
                        validateF(it, model.f, model.userCount, "F")
                    }
                }
                field("F'") {
                    textfield(model.fLine) { intOnly() }.validator {
                        validateF(it, model.fLine, model.f, "F'")
                    }
                }
                field("Random Seed") {
                    textfield(model.randomSeed) { longOnly() }.validator {
                        if (it.isNullOrBlank()) {
                            error("There needs to be a seed")
                        } else {
                            null
                        }
                    }
                }
                field("Byzantine Servers") {
                    textfield(model.byzantineServers) { intOnly() }.validator {
                        if (it.isNullOrBlank() || model.byzantineServers < 0) {
                            error("Byzantine Servers cannot be negative")
                        } else if (model.serverCount <= model.byzantineServers * 3 && model.byzantineServers.value != 0) {
                            error("Byzantine Servers has to be less than ${model.serverCount.value} - 1 / 3 (${(model.serverCount.value - 1) / 3})")
                        } else {
                            null
                        }
                    }
                }
            }
        }

        bottom = buttonbar {
            button("Finish setup", type = ButtonBar.ButtonData.FINISH) {
                enableWhen(model.valid)
                action {
                    HDLTRandom.initSeed(model.randomSeed.value)

                    simulatorController.setupSimulator(model)
                    replaceWith<MasterView>(
                        transition = ViewTransition.Slide(Duration(500.0)),
                        centerOnScreen = true,
                        sizeToScene = true
                    )

                    repeat(model.serverCount.value) {
                        GlobalScope.launch {
                            try {
                                logger.info("Sending initialization parameters to server $it")
                                SetupService(
                                    ManagedChannelBuilder
                                        .forAddress("localhost", BASE_SERVER_PORT + it)
                                        .usePlaintext()
                                        .build()
                                ).broadcastValues(
                                    model.f.value,
                                    model.fLine.value,
                                    model.serverCount.value,
                                    model.byzantineServers.value,
                                    model.randomSeed.value
                                )
                            } catch (e: Exception) {
                                logger.severe("Failed to send initialization parameters to user $it")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDock() {
        model.serverCount.value = SERVER_COUNT
        model.userCount.value = USER_COUNT
        model.rowCount.value = ROW_COUNT
        model.colCount.value = COL_COUNT
        model.epochInterval.value = EPOCH_INTERVAL
        model.randomSeed.value = Random.nextLong()
    }
}
