package sec.locationserver.data

data class LocationReport(
    val user: Int,
    val epoch: Int,
    val coordinates: Coordinates,
)