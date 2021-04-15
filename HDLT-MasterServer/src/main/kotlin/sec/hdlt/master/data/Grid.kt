package sec.hdlt.master.data

import sec.hdlt.master.data.grid.GridCell
import sec.hdlt.master.data.grid.GridDirection
import sec.hdlt.utils.Colors
import kotlin.random.Random

class Grid(
    val rows: Int,
    val cols: Int,
    val f: Int,
    val fLine: Int,
) {
    val cells = mutableMapOf<Int, GridCell>()

    /**
     * Byzantine users are last F users
     */
    fun initGrid(userCount: Int) {
        repeat(userCount) { id ->
            val user = User(id, byzantine = id >= userCount - f)
            val x = Random.nextInt(cols)
            val y = Random.nextInt(rows)

            cells[id] = GridCell(x, y, user)
        }
    }

    fun stepGrid() {
        cells.forEach { (_, cell) ->
            val direction = getPossibleDirections(cell)
            if (direction.isNotEmpty())
                cell.move(direction.random())
        }
    }

    private fun getPossibleDirections(cell: GridCell): List<GridDirection> {
        val directions = GridDirection.values().toMutableList()

        if (cell.x + 1 == cols) {
            directions.remove(GridDirection.RIGHT)
        } else if (cell.x == 0) {
            directions.remove(GridDirection.LEFT)
        }

        if (cell.y + 1 == rows) {
            directions.remove(GridDirection.DOWN)
        } else if (cell.y == 0) {
            directions.remove(GridDirection.UP)
        }

        return checkFLineRestrictions(cell, directions)
    }

    private fun checkFLineRestrictions(currentCell: GridCell, directions: MutableList<GridDirection>): MutableList<GridDirection> {
        GridDirection.values().forEach { direction ->
            var nearCount = 0
            cells.forEach{ cell ->
                if (currentCell.user.byzantine && currentCell.isNear(direction, cell.value)) {
                    nearCount++
                }
            }
            if (nearCount > fLine) {
                directions.remove(direction)
            }
        }
        return directions
    }

    fun getCells(x: Int, y: Int): List<GridCell> {
        return cells.values.filter { cell ->
            cell.x == x && cell.y == y
        }
    }

    fun countCells(x: Int, y: Int): Int {
        return cells.values.filter { cell ->
            cell.x == x && cell.y == y
        }.size
    }

    fun printGrid() {
        repeat(rows) { y ->
            repeat(cols) { x ->
                val cells = getCells(x, y)
                if (cells.isEmpty()) {
                    print("${Colors.WHITE}| ${Colors.YELLOW}0 ${Colors.WHITE}|")
                } else {
                    print("${Colors.WHITE}| ")
                    //cells.forEach { cell -> print("${Colors.RED}${cell.user.id}${Colors.WHITE} ") }
                    print("${Colors.RED}${cells.size} ")
                    print("${Colors.WHITE}|")
                }
            }
            println()
        }
        println()
    }
}
