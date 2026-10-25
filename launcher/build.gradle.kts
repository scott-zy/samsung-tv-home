plugins { alias(libs.plugins.android.application) }
android {
    namespace = "org.s95dhome.launcher"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    defaultConfig { applicationId = "org.s95dhome.launcher"; minSdk = 24; targetSdk = 28; versionCode = 100; versionName = "0.1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8 }
    testOptions { unitTests.isReturnDefaultValues = true }
    // This sideloaded Android-10 TV bridge retains the verified Home-broadcast behavior.
    // Raising the target requires separate platform testing; Google Play publishing is not in scope.
    lint { disable += "ExpiredTargetSdkVersion" }
    sourceSets.getByName("main").java.srcDir("../shared/src/main/java")
}
dependencies {
    implementation("org.java-websocket:Java-WebSocket:1.6.0")
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("org.slf4j:slf4j-nop:2.0.13")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
