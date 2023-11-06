FROM ghcr.io/navikt/baseimages/temurin:21

ENV APP_NAME=familie-baks-mottak
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

COPY ./target/familie-baks-mottak.jar "app.jar"
