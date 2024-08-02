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



## Kort om bruk av Leesah-hendelser
Vi lytter på 4 typer hendelser fra Leesah-topicen til PDL 
### Fødselhendelse
```mermaid
sequenceDiagram
Leesah->>LeesahService: Mottar ny hendelse
LeesahService->>LeesahService: Er fødselshendelse?
LeesahService-->>MottaFødselshendelseTask: Hvis barn er født i Norge og er under 6 måneder gammel
note over MottaFødselshendelseTask: Venter til neste virkedag før task kjøres
MottaFødselshendelseTask->>SendTilBaSakTask: Ikke D-nummer og adressegradering
SendTilBaSakTask->>Familie-ba-sak: Behandle fødselshendelse
```

### Sivilstand, Utflytting, Dødsfall
Barnetrygd lytter på Sivilstand, Utflytting og Dødsfall

Kontanstøtte lytter på Utflytting og Dødsfall

```mermaid
sequenceDiagram
Leesah->>LeesahService: Mottar ny hendelse
LeesahService->>LeesahService: Er hendelse av type dødsfall/sivilstand/utflytting?
LeesahService->>VurderLivshendelseTask: Oppretter task VurderLivshendelseTask for BA og KS
note over VurderLivshendelseTask: Venter 1 time før task kjøres
VurderLivshendelseTask->>familie-ba-sak: Finn berørte brukere
familie-ba-sak-->>VurderLivshendelseTask: Liste med berørte brukere
VurderLivshendelseTask->>Oppgave: Opprett eller oppdater eksisterende VurderLivshendelse-oppgave i Oppgave
```

## Mottak av søknad
```mermaid
sequenceDiagram
baks-soknad-api->>SøknadController: Mottak av søknad
SøknadController->>familie-dokument: Hent alle vedlegg på søknad
familie-dokument-->>SøknadController: Alle vedlegg
SøknadController->>SøknadController: Lagre ned vedlegg og søknad i database
SøknadController->>JournalførSøknadTask: Journalfør søknad
JournalførSøknadTask-->>familie-dokument: Generer PDF av Søknad 
familie-dokument->>JournalførSøknadTask: PDF av Søknad
JournalførSøknadTask->>DokArkiv: Opprett Journalpost i arkiv av generert PDF og Vedlegg
```

## Ruting av journalposter


```mermaid
sequenceDiagram
Kafka->>JournalhendelseService: Ny hendelse av type Journalføring av tema BAR/KON
JournalhendelseService->>JournalhendelseRutingTask: Oppretter ruting task for riktig tema
JournalhendelseRutingTask->>JournalhendelseRutingTask: Sjekk om journalpost kan automatisk journalføres
note over JournalhendelseRutingTask: Søknader sendt inn på digital kanal kan automatisk journalføres
alt Kan automatisk journalføres
    JournalhendelseRutingTask->>familie-ba-sak: Opprett fagsak og behandling
    JournalhendelseRutingTask->>OppdaterOgFerdigstillJournalpostTask: Oppretter task
    OppdaterOgFerdigstillJournalpostTask->>dokarkiv: Ferdigstiller journalpost i arkiv
else Kan ikke automatisk journalføres
    JournalhendelseRutingTask->>OpprettJournalføringOppgaveTask: Opprett task for Journalføringsoppgave hvis man ikke kan automatisk journalføre
    OpprettJournalføringOppgaveTask->>Oppgave: Oppretter JFR-oppgave
end
```
- JournalhendelseRutingTask for BA --> JournalhendelseBarnetrygdRutingTask
- JournalhendelseRutingTask for KS --> JournalhendelseKontantstøtteRutingTask

[1]: https://github.com/navikt/navkafka-docker-compose