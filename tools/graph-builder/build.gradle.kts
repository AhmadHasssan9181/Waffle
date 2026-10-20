plugins {
    java
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Must stay on 1.0: later GraphHopper releases removed offline support.
    implementation("com.graphhopper:graphhopper-core:1.0")
    // The OSM reader lives in a separate artifact in 1.0; needed to import, not to load.
    implementation("com.graphhopper:graphhopper-reader-osm:1.0")
    implementation("org.slf4j:slf4j-simple:1.7.36")
}

application {
    mainClass.set("com.noobdev.waffle.graphbuilder.GraphBuilder")
    applicationDefaultJvmArgs = listOf("-Xmx4g")
}

tasks.register<JavaExec>("verifyGraph") {
    group = "verification"
    description = "Loads the generated graph like the app does and routes a test pair"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.noobdev.waffle.graphbuilder.GraphVerifier")
    jvmArgs("-Xmx2g")
}
