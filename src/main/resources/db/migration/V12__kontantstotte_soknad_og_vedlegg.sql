CREATE TABLE IF NOT EXISTS kontantstotte_soknad(
    id             bigint NOT NULL PRIMARY KEY,
    soknad_json    TEXT NOT NULL,
    fnr            VARCHAR,
    opprettet_tid  TIMESTAMP(3) DEFAULT LOCALTIMESTAMP NOT NULL,
    journalpost_id VARCHAR
);

CREATE SEQUENCE kontantstotte_soknad_seq INCREMENT BY 50 START WITH 1000000 NO CYCLE;

CREATE TABLE IF NOT EXISTS kontantstotte_soknad_vedlegg(
    dokument_id    VARCHAR NOT NULL PRIMARY KEY,
    soknad_id      BIGINT NOT NULL REFERENCES kontantstotte_soknad,
    data           bytea
);

CREATE INDEX ON kontantstotte_soknad_vedlegg (soknad_id);