package sec.hdlt.ha

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


@Serializable
data class Test(val hello: String, val aa: Int)

fun main() {
    println(Json { prettyPrint = true }.encodeToString(Test("Helldsods", 213)))
}
