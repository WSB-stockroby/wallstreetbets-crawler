FROM adoptopenjdk/openjdk11:latest@sha256:8681e7de7d81b5a1f55ebb6832178b9d028577b1fe2727986965bff333bb4bf3

WORKDIR usr/local/wallstreetbets-crawler
COPY config.edn config.edn
COPY secret.edn secret.edn
COPY wallstreetbets-crawler.jar wallstreetbets-crawler.jar

CMD ["sh", "-c", \
    "exec java -cp wallstreetbets-crawler.jar clojure.main --report stderr -m wallstreetbets-crawler"]
