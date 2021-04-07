package sec.hdlt.user.domain

import kotlin.random.Random
import kotlin.streams.toList

class Board {
    private val users = mutableListOf<UserInfo>()

    fun addUser(user: UserInfo) {
        users.add(user)
    }

    fun getUser(id: Int): UserInfo {
        return users.stream()
            .filter { it.id == id }
            .findAny()
            .orElse(null)
    }

    fun getRandomUser(): UserInfo {
        return users[Random.nextInt(users.size)]
    }

    fun getAllUsers(): List<UserInfo> {
        return users
    }

    fun getUserCoords(id: Int): Coordinates {
        return getUser(id).coords
    }

    fun getNearUsers(ownId: Int, coords: Coordinates): List<UserInfo> {
        return users.stream()
            .filter { it.id != ownId && it.isNear(coords) }
            .toList()
    }

    fun clone(): Board {
        val board = Board()
        users.stream()
            .forEach {
                board.addUser(it)
            }
        return board
    }
}