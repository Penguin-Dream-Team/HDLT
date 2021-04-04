package sec.hdlt.master

import javafx.scene.layout.BorderStrokeStyle
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import javafx.scene.text.FontWeight
import javafx.scene.text.TextAlignment
import tornadofx.*

class MasterStyles : Stylesheet() {
    companion object {
        val userCell by cssclass()
        val userCellCircle by cssclass()
        val userCellText by cssclass()
    }

    init {
        userCell {
            borderStyle += BorderStrokeStyle.NONE
            padding = box(0.px)
            userCellCircle {
                fill = Color.BLACK
            }
            userCellText {
                fill = Color.WHITE
                fontWeight = FontWeight.BOLD
            }

            hover {
                userCellCircle {
                    fill = Color.GRAY
                }
                userCellText {
                    fill = Color.BLACK
                }
            }
        }
    }
}
