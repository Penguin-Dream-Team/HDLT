package sec.hdlt.master.viewmodels

import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleLongProperty
import javafx.scene.control.TextField
import sec.hdlt.master.*
import tornadofx.*
import kotlin.random.Random

class MasterSetup {
    val userCountProperty = SimpleIntegerProperty(USER_COUNT)
    val colCountProperty = SimpleIntegerProperty(COL_COUNT)
    val rowCountProperty = SimpleIntegerProperty(ROW_COUNT)
    val epochIntervalProperty = SimpleDoubleProperty(EPOCH_INTERVAL)
    val fProperty = SimpleIntegerProperty(F)
    val fLineProperty = SimpleIntegerProperty(F_LINE)
    val randomSeedProperty = SimpleLongProperty(Random.nextLong())
}

class MasterSetupViewModel : ItemViewModel<MasterSetup>() {
    val userCount = bind(MasterSetup::userCountProperty)
    val colCount = bind(MasterSetup::colCountProperty)
    val rowCount = bind(MasterSetup::rowCountProperty)
    val epochInterval = bind(MasterSetup::epochIntervalProperty)
    val f = bind(MasterSetup::fProperty)
    val fLine = bind(MasterSetup::fLineProperty)
    val randomSeed = bind(MasterSetup::randomSeedProperty)
}

fun TextField.intOnly() {
    filterInput { it.controlNewText.isInt() && it.controlNewText.toInt() >= 0 }
}

fun TextField.longOnly() {
    filterInput { it.controlNewText.isLong() }
}

fun TextField.doubleOnly() {
    filterInput { it.controlNewText.isDouble() && it.controlNewText.toDouble() >= 0 }
}
