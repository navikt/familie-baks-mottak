FROM gcr.io/distroless/java21:nonroot

COPY --chown=nonroot:nonroot ./target/familie-baks-mottak.jar /app/app.jar
WORKDIR /app


ENV APP_NAME=familie-baks-mottak
ENV TZ="Europe/Oslo"

# TLS Config works around an issue in OpenJDK... See: https://github.com/kubernetes-client/java/issues/854
ENTRYPOINT [ "java", "-Djdk.tls.client.protocols=TLSv1.2", "-jar", "/app/app.jar" ]
