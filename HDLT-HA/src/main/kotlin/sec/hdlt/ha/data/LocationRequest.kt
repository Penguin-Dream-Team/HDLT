package sec.hdlt.ha.data

import kotlinx.serialization.Serializable

@Serializable
data class LocationRequest(val coords: Coordinates, val epoch: Int, val signature: String)