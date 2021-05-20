package sec.hdlt.ha.data

import kotlinx.serialization.Serializable

@Serializable
data class EpochLocationResponse(val users: List<Report>, val coords: Coordinates, val epoch: Int, val signature: String) {
    override fun toString(): String {
        return "EPOCH: $epoch\nLOCATION: $coords\nUSERS:${users.joinToString { "\n$it" }}"
    }
}