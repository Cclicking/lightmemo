pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        mavenLocal()
    }
}

includeBuild("../miuix-glass") {
    dependencySubstitution {
        substitute(module("top.yukonga.miuix.kmp:miuix-ui-android")).using(project(":miuix-ui"))
        substitute(module("top.yukonga.miuix.kmp:miuix-ui")).using(project(":miuix-ui"))
        substitute(module("top.yukonga.miuix.kmp:miuix-preference-android")).using(project(":miuix-preference"))
        substitute(module("top.yukonga.miuix.kmp:miuix-preference")).using(project(":miuix-preference"))
        substitute(module("top.yukonga.miuix.kmp:miuix-icons-android")).using(project(":miuix-icons"))
        substitute(module("top.yukonga.miuix.kmp:miuix-icons")).using(project(":miuix-icons"))
        substitute(module("top.yukonga.miuix.kmp:miuix-blur-android")).using(project(":miuix-blur"))
        substitute(module("top.yukonga.miuix.kmp:miuix-blur")).using(project(":miuix-blur"))
        substitute(module("top.yukonga.miuix.kmp:miuix-glass-android")).using(project(":miuix-glass"))
        substitute(module("top.yukonga.miuix.kmp:miuix-glass")).using(project(":miuix-glass"))
        substitute(module("top.yukonga.miuix.kmp:miuix-squircle-android")).using(project(":miuix-squircle"))
        substitute(module("top.yukonga.miuix.kmp:miuix-squircle")).using(project(":miuix-squircle"))
        substitute(module("top.yukonga.miuix.kmp:miuix-nav-android")).using(project(":miuix-nav"))
        substitute(module("top.yukonga.miuix.kmp:miuix-nav")).using(project(":miuix-nav"))
        substitute(module("top.yukonga.miuix.kmp:miuix-shader-android")).using(project(":miuix-shader"))
        substitute(module("top.yukonga.miuix.kmp:miuix-shader")).using(project(":miuix-shader"))
        substitute(module("top.yukonga.miuix.kmp:miuix-core-android")).using(project(":miuix-core"))
        substitute(module("top.yukonga.miuix.kmp:miuix-core")).using(project(":miuix-core"))
    }
}

rootProject.name = "food-android"
include(":app")
