plugins {
    id("java")
}

group = "dev.defied.scorekit"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("redis.clients:jedis:8.0.2")
    compileOnly("org.mongodb:mongodb-driver-sync:5.12.0")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("redis.clients:jedis:8.0.2")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
    testImplementation("org.mongodb:mongodb-driver-sync:5.12.0")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
    testImplementation("org.testcontainers:testcontainers-mysql:2.0.5")
    testImplementation("org.testcontainers:testcontainers-mariadb:2.0.5")
    testImplementation("org.testcontainers:testcontainers-mongodb:2.0.5")
    testImplementation("org.postgresql:postgresql:42.7.14")
    testImplementation("com.mysql:mysql-connector-j:9.7.0")
    testImplementation("org.mariadb.jdbc:mariadb-java-client:3.5.11")
    testImplementation("com.zaxxer:HikariCP:7.1.0")
}

tasks.test {
    useJUnitPlatform()
}
