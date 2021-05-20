package sec.hdlt.server.domain

import kotlinx.serialization.Serializable

@Serializable
data class WitnessRequest(val userId: Int, val epochs: List<Int>,  val signature: String)