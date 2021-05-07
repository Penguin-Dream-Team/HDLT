package sec.hdlt.server.domain

data class ReportInfo (
    val id: Int,
    val epoch: Int,
    val coordinates: Coordinates,
    val serverInfo: String
)
