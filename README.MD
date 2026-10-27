docker run --name exchange-db -e POSTGRES_USER=exchange -e POSTGRES_PASSWORD=exchange -e POSTGRES_DB=exchange -p 5432:
5432 -v exchange-data:/var/lib/postgresql -d postgres:18-alpine

docker rm -f exchange-db
docker volume rm exchange-data

docker run -d --name kafka -p 9092:9092 apache/kafka:latest

-- Create a kafka service
docker exec -it kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic orders.in

-- Send a message
docker exec -it kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic orders.in

-- Consume from topic normally, every time will get the messages again
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic orders.in
--from-beginning

-- Consume from a topic but using a consumer group to prevent re-reading the same messages again.
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic orders.in
--group account-service --from-beginning