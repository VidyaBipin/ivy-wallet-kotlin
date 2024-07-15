plugins {
    id("ivy.feature")
    alias(libs.plugins.screenshot)
}

android {
    namespace = "com.ivy.attributions"
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
}

dependencies {
    implementation(projects.shared.base)
    implementation(projects.shared.domain)
    implementation(projects.shared.ui.core)
    implementation(projects.shared.ui.navigation)

    testImplementation(projects.shared.ui.testing)
    screenshotTestImplementation(projects.shared.ui.testing)
}
