package ec.mnexo

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform