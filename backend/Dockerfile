FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 1001 reloop && mkdir -p /app/data/uploads && chown -R reloop /app
COPY --from=build /app/target/*.jar app.jar
USER reloop
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
