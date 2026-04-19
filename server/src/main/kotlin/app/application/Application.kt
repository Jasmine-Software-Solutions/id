package app.application

fun main() {
    val server = IDServerImpl()
    server.start(Env.PORT)
}