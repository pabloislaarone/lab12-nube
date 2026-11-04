# syntax=docker/dockerfile:1.7
# Imagen multi-etapa para la API y el worker. Se elige el módulo con el argumento MODULE.

# ---------- Etapa 1: compilación con Maven ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

COPY pom.xml .
COPY scoring-core/pom.xml scoring-core/
COPY api/pom.xml api/
COPY worker/pom.xml worker/
COPY scoring-core/src scoring-core/src
COPY api/src api/src
COPY worker/src worker/src

ARG MODULE=api
# La caché de ~/.m2 acelera las recompilaciones (BuildKit)
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl ${MODULE} -am package -DskipTests

# ---------- Etapa 2: imagen liviana solo con el JRE ----------
FROM eclipse-temurin:21-jre-alpine
ARG MODULE=api
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /src/${MODULE}/target/app.jar app.jar
USER app

ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
