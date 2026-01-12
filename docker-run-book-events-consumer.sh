docker container stop koushick123/book-events-consumer:1.0
docker container rm -f koushick123/book-events-consumer:1.0

docker run -e spring.kafka.bootstrap-servers=localhost:9094 -p 8085:9090 koushick123/book-events-consumer:1.0
