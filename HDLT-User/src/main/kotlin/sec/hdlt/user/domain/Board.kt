package sec.hdlt.user.domain

class Board {
    private val users = mutableListOf<UserInfo>()

    fun addUser(user: UserInfo) {
        users.add(user)
    }

    private fun getUser(id: Int): UserInfo {
        return users.stream()
            .filter { it.id == id }
            .findAny()
            .orElse(null)
    }

    fun getRandomUser(): UserInfo {
        return users[Database.random.nextInt(users.size)]
    }

    fun getAllUsers(): List<UserInfo> {
        return users
    }

    fun getUserCoords(id: Int): Coordinates {
        return getUser(id).coords
    }
}