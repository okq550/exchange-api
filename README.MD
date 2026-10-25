docker run --name exchange-db -e POSTGRES_USER=exchange -e POSTGRES_PASSWORD=exchange -e POSTGRES_DB=exchange -p 5432:
5432 -v exchange-data:/var/lib/postgresql -d postgres:18-alpine

docker rm -f exchange-db
docker volume rm exchange-data