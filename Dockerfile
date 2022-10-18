FROM navikt/java:17

ENV APPD_ENABLED=true
ENV APP_NAME=familie-baks-mottak
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

COPY ./target/familie-baks-mottak.jar "app.jar"
