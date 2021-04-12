package sec.hdlt.user.domain

import kotlinx.serialization.Serializable

@Serializable
data class ReportInfo(val id: Int, val epoch: Int, val location: Coordinates, val signature: String, val proofs: List<Proof>)