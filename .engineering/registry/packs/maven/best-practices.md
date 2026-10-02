# Maven Best Practices

1. **Dependency Management**: Use `<dependencyManagement>` in the parent POM to enforce consistent dependency versions across all child modules.
2. **Properties for Versions**: Define all dependency versions in the `<properties>` section at the top of the POM to make upgrades easy and visible.
3. **Maven Wrapper**: Always commit `mvnw` and `mvnw.cmd` to the repository. This guarantees that all developers and CI servers use the exact same Maven version without manual installation.
4. **Plugins**: Explicitly declare the versions of core plugins (like `maven-compiler-plugin` and `maven-surefire-plugin`) to prevent build failures when default versions change.
5. **Scopes**: Use the correct dependency scopes (`provided`, `test`, `runtime`) to minimize the size of the final fat JAR/WAR.
6. **Multi-Module Projects**: Structure large codebases into multi-module Maven projects to enforce architectural boundaries and speed up compilation.
7. **Profiles**: Use `<profiles>` cautiously for environment-specific builds (e.g., building a different artifact for integration tests vs unit tests).
8. **Enforcer Plugin**: Use the `maven-enforcer-plugin` to ban forbidden dependencies or require specific JDK versions.
