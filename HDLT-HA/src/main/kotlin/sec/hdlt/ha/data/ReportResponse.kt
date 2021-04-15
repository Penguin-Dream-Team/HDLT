package sec.hdlt.ha.data

import kotlinx.serialization.Serializable

@Serializable
data class ReportResponse (val id: Int, val epoch: Int, val coords: Coordinates, val serverInfo: String, val signature: String, val proofs: List<Proof>)
