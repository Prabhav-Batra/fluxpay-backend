dependencies {
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-aop")
    api("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    api(project(":shared:exceptions"))
    api(project(":shared:utils"))
}
