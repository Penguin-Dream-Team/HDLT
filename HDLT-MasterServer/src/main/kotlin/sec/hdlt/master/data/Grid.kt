package sec.hdlt.master.data

import sec.hdlt.master.data.grid.GridCell
import sec.hdlt.master.data.grid.GridDirection
import sec.hdlt.utils.Colors
import kotlin.random.Random

class Grid(
    val rows: Int,
    val cols: Int,
) {
    val cells = mutableMapOf<Int, GridCell>()

    fun initGrid(userCount: Int) {
        repeat(userCount) { id ->
            val user = User(id)
            val x = Random.nextInt(cols)
            val y = Random.nextInt(rows)

            cells[id] = GridCell(x, y, user)
        }
    }

    fun stepGrid() {
        cells.forEach { (_, cell) ->
            val direction = getPossibleDirections(cell).random()
            cell.move(direction)
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
