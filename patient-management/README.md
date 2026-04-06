## Run Postgres DB
docker run --name patient-service-db-cont -e POSTGRES_PASSWORD=password -d -p 5432:5432 --network internal -v D:\_learning\_docker\db_volume\patient-service-db:/var/lib/postgresql/data postgres:14.20

docker run --name patient-service --network patient-management_patientmng_net -p 4000:4000 -d patient-service


docker network create patient-management_patientmng_net

docker run --name auth-service-db -e POSTGRES_PASSWORD=password -d -p 5001:5432 --network patient-management_patientmng_net -v D:\_learning\_docker\db_volume\auth-service-db:/var/lib/postgresql/data postgres:14.20
