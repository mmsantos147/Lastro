# Single image: the frontend build is served by Spring Boot, so site and API share the same origin.

FROM node:26-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:25-jdk AS backend
WORKDIR /app/backend
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline
COPY backend/src src
COPY --from=frontend /app/frontend/dist src/main/resources/static
# Tests run in CI (they need Docker for Testcontainers); the image build only packages
RUN ./mvnw -B -ntp package -DskipTests \
    && cp target/lastro-*.jar /app/lastro.jar

FROM eclipse-temurin:25-jre
RUN groupadd --system lastro && useradd --system --gid lastro --home /app lastro
WORKDIR /app
COPY --from=backend /app/lastro.jar lastro.jar
USER lastro
EXPOSE 8080
# Container-aware heap; the VPS is shared with the database
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/lastro.jar"]
