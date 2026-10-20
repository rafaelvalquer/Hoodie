plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.hoodie.app"
    compileSdk = 36
    testBuildType = providers.gradleProperty("hoodieTestBuildType").getOrElse("debug")

    val uploadStore = providers.environmentVariable("HOODIE_UPLOAD_STORE_FILE").orNull
    val uploadStorePassword = providers.environmentVariable("HOODIE_UPLOAD_STORE_PASSWORD").orNull
    val uploadAlias = providers.environmentVariable("HOODIE_UPLOAD_KEY_ALIAS").orNull
    val uploadKeyPassword = providers.environmentVariable("HOODIE_UPLOAD_KEY_PASSWORD").orNull
    val signingValues = listOf(uploadStore, uploadStorePassword, uploadAlias, uploadKeyPassword)
    require(signingValues.all { it.isNullOrBlank() } || signingValues.all { !it.isNullOrBlank() }) {
        "Configure all four HOODIE_UPLOAD_* signing variables together."
    }
    if (!uploadStore.isNullOrBlank()) signingConfigs.create("upload") {
        storeFile = file(uploadStore)
        storePassword = uploadStorePassword
        keyAlias = uploadAlias
        keyPassword = uploadKeyPassword
    }

    defaultConfig {
        applicationId = "com.hoodie.app"
        minSdk = 26
        targetSdk = 36
        // Fonte única de verdade: gradle.properties (HOODIE_VERSION_CODE / HOODIE_VERSION_NAME).
        versionCode = providers.gradleProperty("HOODIE_VERSION_CODE").get().toInt()
        versionName = providers.gradleProperty("HOODIE_VERSION_NAME").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets {
        // Schemas exportados pelo Room: usados pelos testes de migração.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
        getByName("test").assets.srcDir("$projectDir/schemas")
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("boolean", "LOCAL_ENCRYPTED_BACKUP_V1", "false")
            buildConfigField("boolean", "CONSECUTIVE_VISIT_MERGE_V1", "true")
        }
        getByName("release") {
            buildConfigField("boolean", "LOCAL_ENCRYPTED_BACKUP_V1", "false")
            buildConfigField("boolean", "CONSECUTIVE_VISIT_MERGE_V1", "false")
            signingConfig = signingConfigs.findByName("upload")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("homologacao") {
            initWith(getByName("release"))
            applicationIdSuffix = ".homologacao"
            versionNameSuffix = "-homologacao"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            buildConfigField("boolean", "LOCAL_ENCRYPTED_BACKUP_V1", "true")
            buildConfigField("boolean", "CONSECUTIVE_VISIT_MERGE_V1", "true")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("exportIcons", project.findProperty("exportIcons") ?: "false")
            it.systemProperty("exportArt", project.findProperty("exportArt") ?: "false")
            it.systemProperty("artBootstrap", project.findProperty("artBootstrap") ?: "")
            it.systemProperty("npcVisualReview", project.findProperty("npcVisualReview") ?: "false")
            it.systemProperty("npcVisualReviewOutput", project.findProperty("npcVisualReviewOutput") ?: "build/pixel-preview/npc-v3-review")
            it.systemProperty("shoppingNpcReview", project.findProperty("shoppingNpcReview") ?: "false")
            it.systemProperty("shoppingNpcReviewOutput", project.findProperty("shoppingNpcReviewOutput") ?: rootProject.file("docs/npc-art-review/v3/shopping-live").absolutePath)
            it.systemProperty("approveNpcV3Goldens", project.findProperty("approveNpcV3Goldens") ?: "false")
            it.systemProperty("restaurantLiveReview", project.findProperty("restaurantLiveReview") ?: "false")
            it.systemProperty("officeNpcReview", project.findProperty("officeNpcReview") ?: "false")
            it.systemProperty("officeNpcReviewOutput", project.findProperty("officeNpcReviewOutput") ?: rootProject.file("docs/npc-art-review/v3/office-live").absolutePath)
            it.systemProperty("frontSeatedReview", project.findProperty("frontSeatedReview") ?: "false")
            it.systemProperty("sceneBootstrap", project.findProperty("sceneBootstrap") ?: "")
            it.systemProperty("exportSceneArt", project.findProperty("exportSceneArt") ?: "false")
            it.systemProperty("transportReview", project.findProperty("transportReview") ?: "false")
            it.systemProperty("transportReviewOutput", project.findProperty("transportReviewOutput") ?: rootProject.file("docs/transport-art/review").absolutePath)
            it.systemProperty("transportThumbs", project.findProperty("transportThumbs") ?: "false")
            it.systemProperty("transportThumbsOutput", project.findProperty("transportThumbsOutput") ?: rootProject.file("docs/transport-art/thumbnails").absolutePath)
            it.systemProperty("frontSeatedReviewOutput", project.findProperty("frontSeatedReviewOutput") ?: rootProject.file("docs/npc-art-review/v3/front-seated-review").absolutePath)
            // Robolectric + JDK 17/21: acesso a internals de FileDescriptor (SQLite nativo).
            it.jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)
    implementation(libs.work.runtime.ktx)

    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)
    implementation(libs.play.services.location)
    implementation(libs.osmdroid)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
    testImplementation(libs.work.testing)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.work.testing)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    // Espresso novo: o da BOM do Compose não roda em Android 15+ (InputManager.getInstance).
    androidTestImplementation(libs.espresso.core)
    debugImplementation(libs.compose.ui.test.manifest)
    add("homologacaoImplementation", libs.compose.ui.test.manifest)
}
