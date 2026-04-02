plugins {
    id("base")
}

tasks.register("ping") {
    doLast {
        println("pong")
    }
}
