// 项目级 Gradle 配置：仅使用 Google 与 Maven Central 官方仓库，无任何第三方仓库
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "PinyinZoo"
include(":app")
