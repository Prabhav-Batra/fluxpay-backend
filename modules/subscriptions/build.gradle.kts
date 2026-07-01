dependencies {
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.boot:spring-boot-starter-web")

    api(project(":shared:common-dto"))
    api(project(":shared:exceptions"))
    api(project(":shared:utils"))
    api(project(":shared:events"))
    
    api(project(":modules:product-catalog"))
}
