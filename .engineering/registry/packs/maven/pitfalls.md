# Maven Pitfalls

1. ❌ **Transitive Dependency Hell**: Relying on a library without explicitly declaring it because another library brings it in. If the parent library drops it, your build breaks. Always declare what you directly use.
2. ❌ **SNAPSHOT Dependencies in Prod**: Deploying a release artifact that depends on a `-SNAPSHOT` version. SNAPSHOTs are mutable and can change at any time, breaking reproducible builds.
3. ❌ **Massive POM Files**: Letting a single `pom.xml` grow to thousands of lines. Split the project into logical modules.
4. ❌ **Skipping Tests**: Routinely running `mvn clean install -DskipTests`. Tests should always run during a build unless debugging a specific packaging issue.
5. ❌ **Version Conflicts**: Allowing multiple versions of the same transitive dependency (e.g., Jackson or SLF4J) to exist on the classpath. Use `mvn dependency:tree` to find and `<exclude>` conflicts.
6. ❌ **Hardcoding Paths**: Using absolute paths (`C:\Users\...`) in POM files. Always use relative paths or Maven properties (`${project.basedir}`).
