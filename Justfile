uberjar:
  rm wallstreetbets-crawler.jar
  clojure -T:build org.corfield.build/uber :uber-file '"wallstreetbets-crawler.jar"'
  
docker-build TAG: uberjar
  docker build -t wallstreetbets-crawler:{{TAG}} .

dev:
  docker compose down
  docker compose up -d


produce TOPIC:
  docker exec --interactive --tty kafka1 \
    kafka-console-producer --bootstrap-server kafka1:9092 --topic {{TOPIC}}

consume TOPIC:
  docker exec --interactive --tty kafka1 \
    kafka-console-consumer --bootstrap-server kafka1:9092 --topic {{TOPIC}} --from-beginning

docker-bash:
  docker exec -it kafka1 bash