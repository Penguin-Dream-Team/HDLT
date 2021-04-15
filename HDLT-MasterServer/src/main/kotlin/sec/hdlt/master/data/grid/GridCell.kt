package sec.hdlt.master.data.grid

import sec.hdlt.master.data.User

data class GridCell(
    var x: Int,
    var y: Int,
    val user: User
) {
    fun move(direction: GridDirection) {
        when (direction) {
            GridDirection.LEFT -> x--
            GridDirection.RIGHT -> x++
            GridDirection.UP -> y--
            GridDirection.DOWN -> y++
        }
    }

    fun isNear(direction: GridDirection, other: GridCell): Boolean {
        return when (direction) {
            GridDirection.LEFT -> kotlin.math.abs(x-1 - other.x) <= 1 && kotlin.math.abs(y - other.y) <= 1
            GridDirection.RIGHT -> kotlin.math.abs(x+1 - other.x) <= 1 && kotlin.math.abs(y - other.y) <= 1
            GridDirection.UP -> kotlin.math.abs(x - other.x) <= 1 && kotlin.math.abs(y-1 - other.y) <= 1
            GridDirection.DOWN -> kotlin.math.abs(x - other.x) <= 1 && kotlin.math.abs(y+1 - other.y) <= 1
        }
    }
}

