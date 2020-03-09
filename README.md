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

## Kjøring av e2e tester
Ende til ende tester kjøres av GHA ved push. Ønsker du å hoppe over dise må du ha `[e2e skip]` i commit melding for å kunne deploye til dev uten at testene kjører.
Ende til ende testene ligger her: https://github.com/navikt/familie-ba-e2e/tree/master/autotest

## Produksjonssetting
Appen blir produksjonssatt ved å kjøre `tag.sh` som ligger i `.github`. Dette scriptet tagger den seneste commiten i master med det neste versjonsnummeret, og pusher tagen til github-repositoriet.

Hvis den siste tagen er `v0.5`, vil `tag.sh -M` pushe tagen `v1.0`, og `tag.sh -m` pushe tagen `v0.6`.

Ved push av en tag på formen `v*` vil Github Action-workflown `Deploy-Prod` trigges, som bygger en ny versjon av appen, lagrer imaget i Github Packages, og deployer appen til prod-fss.

## Henvendelser
For NAV-interne kan henvendelser rettes til #team-familie på slack. Ellers kan henvendelser rettes via et issue her på github-repoet. 

[1]: https://github.com/navikt/navkafka-docker-compose
