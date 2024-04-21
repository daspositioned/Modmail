plugins {
    id("java")
    id("application")
}

group = "net.dasunterstrich"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("com.impossibl.pgjdbc-ng:pgjdbc-ng:0.8.9")
    implementation("ch.qos.logback:logback-classic:1.4.7")
    implementation("io.github.cdimascio:dotenv-java:2.3.2")

    implementation("net.dv8tion:JDA:5.0.0-beta.23") {
        exclude("opus-java")
    }
}

application {
    mainClass.set("net.dasunterstrich.modmail.Main")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }
}