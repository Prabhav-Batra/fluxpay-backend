dependencies {
    api("org.springframework.boot:spring-boot-starter-data-redis")
    api("com.bucket4j:bucket4j-core:8.1.0")
    api("com.bucket4j:bucket4j-redis:8.1.0")
    
    testImplementation("org.testcontainers:testcontainers:1.19.7")
}
