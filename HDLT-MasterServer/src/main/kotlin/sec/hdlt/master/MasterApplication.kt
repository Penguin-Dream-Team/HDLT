package sec.hdlt.master

import javafx.stage.Stage
import sec.hdlt.master.views.MasterSetupView
import tornadofx.App

class MasterApplication : App(MasterSetupView::class, MasterStyles::class) {

    override fun start(stage: Stage) {
        with(stage) {
            isResizable = false
        }

        super.start(stage)
    }
}