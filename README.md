# familie-baks-mottak
Mottaksapplikasjon for barnetrygd. Lytter på ulike hendelser (fødsler, dødsfall mm) og mottar søknader. 

## Lokal kjøring med Postgres
For å kjøre mot lokal postgress så kan man kjøre DevLauncherPostgres.
```
docker run --name familie-baks-mottak -p 5432:5432 -e POSTGRES_PASSWORD=test -d postgres
docker ps (finn container id)
docker exec -it <container_id> bash
psql -U postgres
CREATE DATABASE "familie-baks-mottak";
```

Det er også en profil DevLauncher, hvor man kan starte appen uten postgres, men med H2. Det anbefales å bruke DevLauncherPostgres

### For å få Lokal kjøring til å integrere mot Kafka
For utvikling lokalt så trenger man ikke å starte opp applikasjonen med Kafka, men hvis man ønsker å teste noe med Kafka, så kan man gjøre følgende:
Klon repo  [navkafka-docker-compose][1].

For å starte kafka:
```bash
cd navkafka-docker-compose
docker-compose build
docker-compose up
```

opprett topics i navkafka fra rota i familie-baks-mottak
```bash
curl -X PUT "http://igroup:itest@localhost:8840/api/v1/oneshot" -H  "Accept: application/json" -H  "Content-Type: application/json" --data "./src/test/resources/lokal-kafka-topics.json"
```
Sett property
```
funksjonsbrytere:
  kafka.enabled: true
```

## Produksjonssetting
Appen blir produksjonssatt ved push til main

## Henvendelser
For NAV-interne kan henvendelser rettes til #team-familie på slack. Ellers kan henvendelser rettes via et issue her på github-repoet.

[1]: https://github.com/navikt/navkafka-docker-compose
