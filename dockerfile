FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY src ./src
RUN mkdir -p /app/out && javac -d /app/out src/*.java

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/out ./
RUN mkdir -p /app/data

ENV PORT=8080
ENV ROLE=LEADER
ENV DATA_FILE=/app/data/data.db

EXPOSE 8080
CMD ["java", "DockerContainerMain"]
