docker container stop kafka-consumer-service:1.0
docker container rm -f  kafka-consumer-service:1.0

docker run -e spring.kafka.bootstrap-servers=localhost:9094 -p 8085:9090 kafka-consumer-service:1.0
