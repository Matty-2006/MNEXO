package ec.mnexo

fun sayHello(to: String): String {
    val name = to.trim()
    return if (name.isEmpty()) "¡Hola!" else "¡Hola, $name!"
}
