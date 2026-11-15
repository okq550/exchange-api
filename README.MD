docker run --name exchange-db -e POSTGRES_USER=exchange -e POSTGRES_PASSWORD=exchange -e POSTGRES_DB=exchange -p 5432:
5432 -v exchange-data:/var/lib/postgresql -d postgres:18-alpine

docker rm -f exchange-db
docker volume rm exchange-data

docker run -d --name kafka -p 9092:9092 apache/kafka:latest

docker run -d --name prometheus -p 9090:9090 \
-v "$(pwd)/monitoring/prometheus.yml:/etc/prometheus/prometheus.yml" \
prom/prometheus

docker run -d --name grafana -p 3000:3000 grafana/grafana

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

Accounts
# 1. Account 1: $20,000, no shares
curl -s -X POST http://localhost:8080/accounts -H 'Content-Type: application/json' \
-d '{"initialCashInCents": 2000000, "initialShares": 0}' | jq

# 2. Account 2: no cash, 100 shares
curl -s -X POST http://localhost:8080/accounts -H 'Content-Type: application/json' \
-d '{"initialCashInCents": 0, "initialShares": 100}' | jq

# 3. Account 3: $10,000, no shares
curl -s -X POST http://localhost:8080/accounts -H 'Content-Type: application/json' \
-d '{"initialCashInCents": 1000000, "initialShares": 0}' | jq

Orders
# 4. Account 1: BUY 10 @ 100.00 → rests
curl -s -X POST http://localhost:8080/orders -H 'Content-Type: application/json' \
-d '{"accountId": 1, "symbol": "AAPL", "side": "BUY", "quantity": 10, "priceInCents": 10000}' | jq

# 5. Account 2: SELL 10 @ 99.00 → trades 10 @ 100.00
curl -s -X POST http://localhost:8080/orders -H 'Content-Type: application/json' \
-d '{"accountId": 2, "symbol": "AAPL", "side": "SELL", "quantity": 10, "priceInCents": 9900}' | jq

# 6. Account 2: SELL 20 @ 101.00 → rests
curl -s -X POST http://localhost:8080/orders -H 'Content-Type: application/json' \
-d '{"accountId": 2, "symbol": "AAPL", "side": "SELL", "quantity": 20, "priceInCents": 10100}' | jq

# 7. Account 3: BUY 5 @ 102.00 → trades 5 @ 101.00 (price improvement)
curl -s -X POST http://localhost:8080/orders -H 'Content-Type: application/json' \
-d '{"accountId": 3, "symbol": "AAPL", "side": "BUY", "quantity": 5, "priceInCents": 10200}' | jq

# 8. Account 1: BUY 15 @ 101.00 → trades 15 @ 101.00
curl -s -X POST http://localhost:8080/orders -H 'Content-Type: application/json' \
-d '{"accountId": 1, "symbol": "AAPL", "side": "BUY", "quantity": 15, "priceInCents": 10100}' | jq

Checks
sleep 2

# 9. Account 1 trades → 2 trades: 15 @ 10100, then 10 @ 10000
curl -s http://localhost:8080/accounts/1/trades | jq

# 10. Account 2 trades → 3 trades: 15 @ 10100, 5 @ 10100, 10 @ 10000
curl -s http://localhost:8080/accounts/2/trades | jq

# 11. Account 3 trades → 1 trade: 5 @ 10100
curl -s http://localhost:8080/accounts/3/trades | jq

# 12. Account 1 → cash 1748500, reserved 0, shares 25
curl -s http://localhost:8080/accounts/1 | jq

# 13. Account 2 → cash 302000, shares 70, reservedShares 0
curl -s http://localhost:8080/accounts/2 | jq

# 14. Account 3 → cash 949500, reserved 0, shares 5
curl -s http://localhost:8080/accounts/3 | jq


# Useful loop for creating orders
while true; do                                                                                               
curl -s -X POST http://localhost:8080/orders -H 'Content-Type: application/json' \
-d '{"accountId": 1, "symbol": "AAPL", "side": "BUY", "quantity": 1, "priceInCents": 10000}' > /dev/null

curl -s -X POST http://localhost:8080/orders -H 'Content-Type: application/json' \
-d '{"accountId": 2, "symbol": "AAPL", "side": "SELL", "quantity": 1, "priceInCents": 10000}' > /dev/null

sleep 0.2
done

# Usefull command to find all trade traces:
docker compose logs --no-log-prefix exchange-api matching-engine | grep -E "(orderId|buyOrder|sellOrder)=5\b"

Grafana Dashboard panels:

Title	Query
Orders per second	sum(rate(http_server_requests_seconds_count{uri="/orders"}[1m]))
Consumer lag	kafka_consumer_fetch_manager_records_lag{topic=~"orders.in|trades|cancels"}
Order request p99 (seconds)	histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{uri="/orders"}[1m])))
GC pause max (seconds)	max(jvm_gc_pause_seconds_max)

Deploy Steps

git update-index --chmod=+x mvnw
git commit -m "Make Maven wrapper executable"

Kubernetes	What it is	Compose equivalent
Pod	one running instance of a container	a running container
Deployment	“keep N pods of this image running”	a service: entry
Service	a stable name and address in front of pods	the service name on the Compose network
Secret	sensitive config, such as passwords	values in environment:
Probes	liveness and readiness checks	healthcheck:

Step 1: tools and a local cluster. kind runs a Kubernetes cluster inside Docker on your Mac.

brew install kind kubectl
kind create cluster --name exchange
kubectl get nodes

Step 2: put your images into the cluster. The cluster can’t see your laptop’s local images by default, so load them in. First, stop Compose to free the ports (docker compose down in IdeaProjects), then build fresh images from each project folder, and load them:

docker build -t exchange-api:dev ./exchange-api
docker build -t matching-engine:dev ./matching-engine
kind load docker-image exchange-api:dev matching-engine:dev --name exchange

Step 4: apply it and watch.

kubectl apply -f k8s/exchange.yaml
kubectl get pods --watch

Step 5: reach the API. Forward a port from your laptop into the cluster:

kubectl port-forward service/exchange-api 8080:8080

* selector and labels are how the pieces find each other. A Deployment manages the pods with label app: kafka, and the Service named kafka sends traffic to pods with that same label. The Service name is what makes kafka:9092 resolve, exactly as on the Compose network.
* The password lives in a Secret, referenced by name. The Deployments never contain it.
* Three probes per Java service. The startup probe gives the app up to 2 minutes (60 × 2 s) to boot, and liveness checks don’t start until it passes, so a slow startup isn’t mistaken for a hang. Readiness uses the endpoints you built in Lesson 9.4: for the engine, it stays not-ready until its replay finishes.
* imagePullPolicy: IfNotPresent tells Kubernetes to use the image you loaded, rather than trying to download exchange-api:dev from a registry.
* PostgreSQL has no persistent storage here: delete its pod, and the data is gone. That’s acceptable for learning. Production uses a StatefulSet with a persistent volume, or more commonly a managed database outside Kubernetes.


Rebuild and re-deploy

docker build -t exchange-api:dev ./exchange-api
kind load docker-image exchange-api:dev --name exchange
kubectl rollout restart deployment exchange-api
kubectl scale deployment exchange-api --replicas=2

