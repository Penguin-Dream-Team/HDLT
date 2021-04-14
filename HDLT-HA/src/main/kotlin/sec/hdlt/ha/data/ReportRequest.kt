package sec.hdlt.ha.data

import kotlinx.serialization.Serializable

@Serializable
data class ReportRequest(val id: Int, val epoch: Int, val signature: String)
