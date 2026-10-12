# Etapa 1: compila y empaqueta el jar con Maven.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Los tests corren en el IDE y en CI; aca se saltean para que la imagen arme rapido.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

# Etapa 2: imagen final, solo con el JRE y el jar.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/user-service-*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
