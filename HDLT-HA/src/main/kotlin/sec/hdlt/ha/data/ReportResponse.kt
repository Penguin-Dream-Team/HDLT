package sec.hdlt.ha.data

import kotlinx.serialization.Serializable

@Serializable
data class ReportResponse (val id: Int, val epoch: Int, val coords: Coordinates, val signature: String)
