package sec.hdlt.ha.data

import kotlinx.serialization.Serializable

@Serializable
data class EpochLocationRequest(val coords: Coordinates, val epoch: Int, val signature: String)