package sec.locationserver.data

class Coordinates(val x: Int, val y: Int) {
    fun isNear(other: Coordinates): Boolean {
        return kotlin.math.abs(x - other.x) <= 1 && kotlin.math.abs(y - other.y) <= 1
    }

    override fun equals(other: Any?): Boolean {
        if (other is Coordinates) {
            return x == other.x && y == other.y
        }

        return false
    }

    override fun toString(): String {
        return "($x,$y)"
    }

    override fun hashCode(): Int {
        var result = x
        result = 31 * result + y
        return result
    }
}
