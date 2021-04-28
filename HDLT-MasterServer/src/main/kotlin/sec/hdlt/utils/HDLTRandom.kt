package sec.hdlt.utils

import kotlin.random.Random

object HDLTRandom {
    lateinit var random: Random
    var seed: Long = 0

    fun initSeed(newSeed: Long) {
        seed = newSeed
        random = Random(newSeed)
    }
}