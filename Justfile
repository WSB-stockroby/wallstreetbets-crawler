uberjar:
  rm wallstreetbets-crawler.jar
  clojure -T:build org.corfield.build/uber :uber-file '"wallstreetbets-crawler.jar"'
  
docker-build TAG: uberjar
  docker build -t wallstreetbets-crawler:{{TAG}} .
