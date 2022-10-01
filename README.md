# familie-baks-mottak
Mottaksapplikasjon for barnetrygd. Lytter på ulike hendelser (fødsler, dødsfall mm) og mottar søknader. 

## Lokal kjøring
Appen bygges med maven og kan kjøres fra DevLauncher-klassen. Sett `-Dspring.profiles.active=dev` under Edit Configurations -> VM Options. Lokalt må man kjøre serveren sammen med [navkafka-docker-compose][1].
Topicene vi lytter på må da opprettes via deres api med følgende data:
```
{
  "topics": [
    {
      "topicName": "aapen-person-pdl-leesah-v1",
      "members": [
        {"member":"srvc01", "role":"CONSUMER"}
      ],
      "numPartitions": 3
    },
    {
      "topicName": "aapen-dok-journalfoering-v1-q1",
      "members": [
        {"member":"srvc01", "role":"CONSUMER"}
      ],
      "numPartitions": 3
    },
    {
      "topicName": "aapen-person-pdl-aktor-v1",
      "members": [
        {"member":"srvc01", "role":"CONSUMER"}
      ],
      "numPartitions": 3
    },
  ]
}
```
Dette kan gjøres via følgende kommandoer:\
(for Windows, kjør disse via Cygwin)
```
curl -X POST "http://igroup:itest@localhost:8840/api/v1/topics" -H "Accept: application/json" -H "Content-Type: application/json" --data "{"name": "aapen-person-pdl-leesah-v1", "members": [{ "member": "srvc01", "role": "CONSUMER" }], "numPartitions": 3 }"

curl -X POST "http://igroup:itest@localhost:8840/api/v1/topics" -H "Accept: application/json" -H "Content-Type: application/json" --data "{"name": "aapen-dok-journalfoering-v1-q1", "members": [{ "member": "srvc01", "role": "CONSUMER" }], "numPartitions": 3 }"

curl -X POST "http://igroup:itest@localhost:8840/api/v1/topics" -H "Accept: application/json" -H "Content-Type: application/json" --data "{"name": "aapen-person-pdl-aktor-v1", "members": [{ "member": "srvc01", "role": "CONSUMER" }], "numPartitions": 3 }"
```

Se README i navkafka-docker-compose for mer info om hvordan man kjører den og kaller apiet.

## Lokal kjøring med Postgres
For å kjøre mot lokal postgress så kan man kjøre DevLauncherPostgress.
```
docker run --name familie-baks-mottak -p 5432:5432 -e POSTGRES_PASSWORD=test -d postgres
docker ps (finn container id)
docker exec -it <container_id> bash
psql -U postgres
CREATE DATABASE "familie-baks-mottak";
```

Man må legge følgende endring til i application-postgres.yaml under spring-seksjonen

```
+  cloud:
+    vault:
+      enabled: false
+      database:
+        role: postgres
```

og `-Dspring.profiles.active=postgres` under Edit Configurations -> VM Options.

## Kjøring av e2e tester
Ende til ende tester kjøres av GHA ved push. Ønsker du å hoppe over dise må du ha `[e2e skip]` i commit melding for å kunne deploye til dev uten at testene kjører.
Ende til ende testene ligger her: https://github.com/navikt/familie-ba-e2e/tree/master/autotest 

## Produksjonssetting
Appen blir produksjonssatt ved push til master

## Henvendelser
For NAV-interne kan henvendelser rettes til #team-familie på slack. Ellers kan henvendelser rettes via et issue her på github-repoet.

[1]: https://github.com/navikt/navkafka-docker-compose
