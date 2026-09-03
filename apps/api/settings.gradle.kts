// JDK 21 이 없는 PC 에서도 빌드가 되도록 Gradle 이 툴체인을 직접 내려받는다.
// 시스템에 설치하지 않고 ~/.gradle/jdks 에만 두므로 PC 환경을 건드리지 않는다.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "litmood-api"
