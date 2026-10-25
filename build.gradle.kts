import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

subprojects {
    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension> {
            val path=providers.environmentVariable("RELEASE_STORE_FILE").orNull
            val password=providers.environmentVariable("RELEASE_STORE_PASSWORD").orNull
            if(path!=null||password!=null){
                require(!path.isNullOrBlank()&&!password.isNullOrBlank()) { "Both release keystore environment variables are required" }
                val releaseKey=signingConfigs.create("publicRelease"){
                    storeFile=file(path);storePassword=password
                    keyAlias=providers.environmentVariable("RELEASE_KEY_ALIAS").orElse("release").get()
                    keyPassword=providers.environmentVariable("RELEASE_KEY_PASSWORD").orElse(password).get()
                }
                buildTypes.getByName("release").signingConfig=releaseKey
            }
        }
    }
}
