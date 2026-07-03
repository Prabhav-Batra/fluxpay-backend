plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    
    // Dependencies on shared and infrastructure modules
    implementation(project(":infrastructure:database"))
    implementation(project(":infrastructure:cache"))
    
    // Phase 2 Modules
    implementation(project(":modules:authentication"))
    implementation(project(":modules:merchant-management"))
    implementation(project(":modules:organization-management"))
    implementation(project(":modules:api-keys"))
    
    // Phase 3 Modules
    implementation(project(":modules:product-catalog"))
    implementation(project(":modules:orders"))
    implementation(project(":infrastructure:external"))
    
    // Phase 4 Modules
    implementation(project(":modules:payments"))
    implementation(project(":modules:gateway-framework"))
    
    // Phase 5 Modules
    implementation(project(":modules:subscriptions"))
    implementation(project(":modules:invoices"))
    implementation(project(":modules:coupons"))
    
    // Phase 6 Modules
    implementation(project(":modules:webhooks"))
    // implementation(project(":modules:notifications"))
    // implementation(project(":modules:audit-logs"))
    // implementation(project(":modules:analytics"))
    implementation(project(":modules:customers"))
    implementation(project(":shared:exceptions"))
    implementation(project(":shared:security"))
    implementation(project(":shared:common-dto"))
    implementation(project(":shared:utils"))
    
    // Dependencies on feature modules (will be uncommented as built)
    // implementation(project(":modules:authentication"))
    // implementation(project(":modules:merchant-management"))
}

tasks.withType<Jar> {
    enabled = false
}

tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    enabled = true
}
