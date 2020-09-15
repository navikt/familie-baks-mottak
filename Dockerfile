FROM navikt/java:11-appdynamics

ENV APPD_ENABLED=true
ENV APP_NAME=familie-ba-mottak

COPY ./target/familie-ba-mottak.jar "app.jar"
