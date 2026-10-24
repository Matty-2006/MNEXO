package ec.mnexo

class Greeting(private val assistantName: String = "MNEXO") {
    fun greet(): String {
        return sayHello(assistantName)
    }
}
