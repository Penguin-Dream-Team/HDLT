package sec.hdlt.ha.data

import kotlinx.serialization.Serializable

@Serializable
data class Coordinates(val x: Int, val y: Int) {
    override fun toString(): String {
        return "($x,$y)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Coordinates

        if (x != other.x) return false
        if (y != other.y) return false

        return true
    }

    override fun hashCode(): Int {
        var result = x
        result = 31 * result + y
        return result
    }
}
