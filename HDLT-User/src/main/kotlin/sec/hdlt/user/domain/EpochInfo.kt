package sec.hdlt.user.domain

data class EpochInfo(var position: Coordinates, var epoch: Int, var board: Board, var users: MutableSet<Int>)