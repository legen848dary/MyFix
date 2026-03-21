FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

COPY build/install/TheFixClient/ /app/

RUN chmod +x /app/bin/TheFixClient && \
    mkdir -p /app/logs/thefixclient/quickfixj

EXPOSE 8081

CMD ["sh", "-c", "exec /app/bin/TheFixClient"]

