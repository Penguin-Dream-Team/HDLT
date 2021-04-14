package sec.hdlt.ha.data

import kotlinx.serialization.Serializable

@Serializable
data class Coordinates(val x: Int, val y: Int) {
    override fun toString(): String {
        return "($x,$y)"
    }
}
