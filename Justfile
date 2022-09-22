uberjar:
  rm wallstreetbets-crawler.jar
  clojure -T:build org.corfield.build/uber :uber-file '"wallstreetbets-crawler.jar"'
  
docker-build TAG: uberjar
  docker build -t wallstreetbets-crawler:{{TAG}} .
  docker tag wallstreetbets-crawler:{{TAG}} wallstreetbets-crawler:latest

docker-build-gcp VERSION: uberjar
  docker build -t eu.gcr.io/white-academy-363312/wallstreetbets-crawler:{{VERSION}} .
  docker tag eu.gcr.io/white-academy-363312/wallstreetbets-crawler:{{VERSION}} wallstreetbets-crawler:latest

docker-login-gcp:
  cat gcr_key_file.json  | docker login -u _json_key --password-stdin https://eu.gcr.io

docker-push-gcp VERSION: docker-login-gcp
  just docker-build-gcp {{VERSION}}
  docker push eu.gcr.io/white-academy-363312/wallstreetbets-crawler:{{VERSION}}

dev:
  docker compose down
  docker compose up -d

# TODO: the requests will time out for some reason, fix it (maybe AOT?, monitor the the container to find the root cause)
# NOTE: this only works if there is a wallstreetbets-crawler-network network defined in the compose file
run: dev
  docker run -i --rm --net wallstreetbets-crawler-network wallstreetbets-crawler:latest


produce TOPIC:
  docker exec --interactive --tty kafka1 \
    kafka-console-producer --bootstrap-server kafka1:9092 --topic {{TOPIC}}

consume TOPIC:
  docker exec --interactive --tty kafka1 \
    kafka-console-consumer --bootstrap-server kafka1:9092 --topic {{TOPIC}} --property print.key=true --property key.separator=" -> " --from-beginning

docker-bash:
  docker exec -it kafka1 bash