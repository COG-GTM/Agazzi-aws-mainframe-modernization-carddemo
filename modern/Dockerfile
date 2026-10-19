# syntax=docker/dockerfile:1
# Multi-stage build of carddemo-app (build context: modern/).
FROM eclipse-temurin:21-jdk AS build
# Optional Maven Central mirror (e.g. when repo1 rate-limits the build host).
ARG MAVEN_MIRROR_URL=""
ENV MVNW_REPOURL=${MAVEN_MIRROR_URL}
WORKDIR /src
COPY . .
RUN if [ -n "$MAVEN_MIRROR_URL" ]; then mkdir -p /root/.m2 && printf '<settings><mirrors><mirror><id>mirror</id><url>%s</url><mirrorOf>central</mirrorOf></mirror></mirrors></settings>' "$MAVEN_MIRROR_URL" > /root/.m2/settings.xml; fi \
 && ./mvnw -B -q -pl carddemo-app -am package -DskipTests -Dcheckstyle.skip -Dspotless.check.skip=true -Djacoco.skip=true

FROM eclipse-temurin:21-jre
RUN groupadd --system carddemo && useradd --system --gid carddemo carddemo
WORKDIR /app
COPY --from=build /src/carddemo-app/target/carddemo-app.jar app.jar
USER carddemo
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
