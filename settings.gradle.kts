pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "fluxpay-backend"

// Apps
include("apps:fluxpay-api")

// Infrastructure
include("infrastructure:database")
include("infrastructure:cache")
include("infrastructure:external")

// Shared
include("shared:common-dto")
include("shared:exceptions")
include("shared:security")
include("shared:utils")
include("shared:events")

// Modules
include("modules:authentication")
include("modules:merchant-management")
include("modules:organization-management")
include("modules:api-keys")
include("modules:product-catalog")
include("modules:orders")
include("modules:payments")
include("modules:gateway-framework")
include("modules:subscriptions")
include("modules:coupons")
include("modules:invoices")
include("modules:webhooks")
include("modules:notifications")
include("modules:analytics")
include("modules:audit-logs")
include("modules:customers")
