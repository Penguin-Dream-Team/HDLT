package sec.hdlt.user.domain

import sec.hdlt.user.BASE_PORT

data class UserInfo(val id: Int, val coords: Coordinates, val port: Int = id + BASE_PORT) {
    fun isNear(otherCoords: Coordinates): Boolean  {
        return coords.isNear(otherCoords)
    }
}