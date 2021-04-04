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
}

