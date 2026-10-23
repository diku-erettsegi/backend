FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl tar && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 diku && useradd --uid 10001 --gid diku --no-create-home diku \
    && mkdir -p /app/uploads && chown -R diku:diku /app
WORKDIR /app
COPY --from=build /build/target/backend-*.jar app.jar
USER diku
EXPOSE 8080
HEALTHCHECK --interval=15s --start-period=45s CMD curl --fail --silent http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java","-jar","/app/app.jar"]
