pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "FolioScan"

include(":app")
include(":core:model")
include(":core:designsystem")
include(":core:storage")
include(":core:database")
include(":core:vision")
include(":core:ocr")
include(":core:identity")
include(":core:pdf")
include(":core:scanner")
include(":feature:home")
include(":feature:capture")
include(":feature:editor")
include(":feature:detail")
include(":feature:search")
include(":feature:folders")
include(":feature:vault")
include(":feature:settings")
