package sec.hdlt.master.views

import javafx.beans.property.SimpleListProperty
import javafx.scene.Cursor
import javafx.scene.paint.Color
import javafx.scene.text.TextAlignment
import sec.hdlt.master.MasterStyles
import sec.hdlt.master.controllers.SimulatorController
import sec.hdlt.master.data.grid.GridCell
import tornadofx.*

class GridView : Fragment("Grid View") {
    private val simulatorController: SimulatorController by inject()

    private val cellsProperty = SimpleListProperty<GridCell>(observableListOf())
    private val cells by cellsProperty

    private val grid = simulatorController.simulator!!.grid
    private val rows = grid.rows
    private val cols = grid.cols

    override fun onDock() {
        cells.addAll(grid.cells.values)

        subscribe<GridUpdateRequest> {
            runLater {
                cells.clear()
                cells.addAll(grid.cells.values)
            }
        }
    }

    override val root = stackpane {
        val cellSize = 25.0
        val lineWidth = 1.25
        val fullHeight = (cellSize) * rows
        val fullWidth = (cellSize) * cols
        val offsetX = fullWidth / 2
        val offsetY = fullHeight / 2

        prefWidth = fullWidth + 20
        prefHeight = fullHeight + 20

        repeat(cols) { x ->
            repeat(rows) { y ->
                rectangle {
                    fill = Color.WHITE
                    stroke = Color.RED
                    strokeWidth = lineWidth
                    translateX = x * (cellSize) - offsetX + cellSize / 2
                    translateY = y * (cellSize) - offsetY + cellSize / 2
                    width = cellSize
                    height = cellSize
                }
            }
        }
        stackpane {
            bindChildren(cellsProperty) { cell ->
                val x = cell.x
                val y = cell.y
                val count = grid.countCells(x, y)
                button {
                    prefHeight = cellSize / 2.5
                    prefWidth = prefHeight
                    cursor = Cursor.HAND
                    translateX = x * (cellSize) - offsetX + cellSize / 2
                    translateY = y * (cellSize) - offsetY + cellSize / 2
                    removeClass("button")
                    addClass(MasterStyles.userCell)
                    stackpane {
                        circle {
                            radius = (cellSize) / 2.5
                            addClass(MasterStyles.userCellCircle)
                        }
                        text {
                            text = "$count"
                            fill = Color.WHITE
                            textAlignment = TextAlignment.CENTER
                            addClass(MasterStyles.userCellText)
                        }
                    }
                }
            }
        }
    }
}
