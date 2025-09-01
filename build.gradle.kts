plugins {
    id("java")
    id("application")
    id("io.ebean") version "16.0.0-RC4"
}

group = "com.team293"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.slack.api:bolt-socket-mode:1.45.3")
    implementation("javax.websocket:javax.websocket-api:1.1")
    implementation("org.glassfish.tyrus.bundles:tyrus-standalone-client:1.20")
    implementation("org.slf4j:slf4j-simple:1.7.36")

    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.0-rc1")

    implementation("io.smallrye.config:smallrye-config:3.13.4")

    implementation("org.reflections:reflections:0.10.2")

    implementation(platform("io.ebean:ebean-bom:16.0.0"))
    implementation("io.ebean:ebean:16.0.0")
    implementation("io.ebean:ebean-querybean:16.0.0")
    annotationProcessor("io.ebean:querybean-generator:16.0.0") // generates query beans for type-safe queries
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("io.ebean:ebean-ddl-generator:16.0.0")
}

application {
    mainClass.set("com.team293.Main")
}

tasks.named<JavaExec>("run") {
    // gradle run -DslackLogLevel=debug
    systemProperty(
        "org.slf4j.simpleLogger.log.com.slack.api",
        System.getProperty("slackLogLevel")
    )

    environment("SLACK_APP_TOKEN", System.getenv("SLACK_APP_TOKEN"))
    environment("SLACK_BOT_TOKEN", System.getenv("SLACK_BOT_TOKEN"))
}

ebean {
    debugLevel = 1 // prints "ebean-enhance> cls: ..." so you can see it working
}