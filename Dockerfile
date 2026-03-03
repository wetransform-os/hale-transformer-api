FROM eclipse-temurin:17.0.18_8-jdk AS builder

WORKDIR /app

# Copy the Gradle wrapper files
COPY gradlew .
COPY gradle gradle

# Copy the project build files
COPY build.gradle .
COPY settings.gradle .

# Copy the project source
COPY src src

# Build the application
RUN ./gradlew bootJar

RUN java -Djarmode=layertools -jar build/libs/*.jar extract --destination build/extracted/

# Create the final image
FROM eclipse-temurin:17.0.18_8-jre

WORKDIR /app

# Run as non-root
RUN groupadd hale && useradd -d /app -g hale hale && chown hale:hale /app
USER hale

# Copy the built JAR file from the builder image
#COPY --from=builder /app/build/libs/*.jar app.jar
COPY --from=builder --chown=hale /app/build/extracted/dependencies/ ./
COPY --from=builder --chown=hale /app/build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=hale /app/build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=hale /app/build/extracted/application/ ./

# Expose the port
EXPOSE 8080

# Define the command to run the application when the container starts
ENTRYPOINT ["java", "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED", "--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED", "org.springframework.boot.loader.launch.JarLauncher"]
