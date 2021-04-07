package sec.hdlt.server.data

data class LocationReport(
    val user: Int,
    val epoch: Int,
    val coordinates: Coordinates,
)