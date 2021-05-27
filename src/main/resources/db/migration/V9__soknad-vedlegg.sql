CREATE TABLE IF NOT EXISTS soknad_vedlegg(
  dokument_id    VARCHAR NOT NULL PRIMARY KEY,
  soknad_id      BIGINT NOT NULL REFERENCES soknad,
  data           bytea
);

CREATE INDEX ON soknad_vedlegg (soknad_id)