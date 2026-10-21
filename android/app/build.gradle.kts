plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace="dev.cardadvisor.android"
 compileSdk=35
 defaultConfig { applicationId="dev.cardadvisor.android"; minSdk=26; targetSdk=35; versionCode=1; versionName="0.1.0"; testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner" }
 buildFeatures { compose=true; buildConfig=true }
 defaultConfig { buildConfigField("String","API_BASE_URL","\"http://127.0.0.1:18080\"") }
 buildTypes { getByName("release") {
  val endpoint=providers.gradleProperty("advisor.apiUrl").orElse("https://unconfigured.invalid").get()
  require(endpoint.matches(Regex("https://[A-Za-z0-9.-]+(?::[0-9]+)?"))) { "Release API must use HTTPS and contain only a host/port." }
  buildConfigField("String","API_BASE_URL","\"$endpoint\"")
 } }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }
}
dependencies {
 testImplementation("junit:junit:4.13.2")
 implementation(platform("androidx.compose:compose-bom:2025.04.01"))
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.activity:activity-compose:1.10.1")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
 debugImplementation("androidx.compose.ui:ui-tooling")
 // Instrumentation calls Trace.forceEnableAppTracing (>=1.1). Keep the target APK
 // aligned with Espresso's 1.2.0 so its older transitive class cannot shadow it.
 debugImplementation("androidx.tracing:tracing:1.2.0")
 androidTestImplementation("androidx.tracing:tracing:1.2.0")
 androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
 androidTestImplementation("androidx.compose.ui:ui-test-junit4")
 // 3.7 replaces the removed reflective InputManager.getInstance access on API37.
 androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
 androidTestImplementation("androidx.test:core:1.7.0")
 androidTestImplementation("androidx.test:runner:1.7.0")
 androidTestImplementation("androidx.test.ext:junit:1.3.0")
}

