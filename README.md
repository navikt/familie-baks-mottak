# familie-ba-mottak
Mottaksapplikasjon for barnetrygd. Lytter på ulike hendelser (fødsler, dødsfall mm) og mottar søknader. 

## Lokal kjøring
Appen bygges med maven og kan kjøres fra DevLauncher-klassen. Lokalt må man kjøre serveren sammen med [navkafka-docker-compose][1]. Topicen vi lytter på må da opprettes via deres api med følgende data:
```
{
  "topics": [
    {
      "topicName": "aapen-person-pdl-leesah-v1",
      "members": [
        {"member":"srvc01", "role":"CONSUMER"}
      ],
      "numPartitions": 3
    }
  ]
}
```
Se README i navkafka-docker-compose for mer info om hvordan man kjører den og kaller apiet.

## Lokal kjøring med Postgres
For å kjøre mot lokal postgress så kan man kjøre DevLauncherPostgress.
```
docker run --name postgres -p 5432:5432 -e POSTGRES_PASSWORD=postgres -e POSTGRES_USER=postgres -d postgres
```

## Henvendelser
For NAV-interne kan henvendelser rettes til #team-familie på slack. Ellers kan henvendelser rettes via et issue her på github-repoet. 

[1]: https://github.com/navikt/navkafka-docker-compose