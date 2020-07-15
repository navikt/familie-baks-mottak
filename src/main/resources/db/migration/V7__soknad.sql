CREATE TABLE IF NOT EXISTS soknad(
  id             UUID NOT NULL PRIMARY KEY, --UUID eller VARCHAR? Må UUID settes manuelt?
  soknad_json    BYTEA NOT NULL, -- BYTEA og ikke json(b)
  fnr            VARCHAR(50), -- Hvorfor 50?
  task_opprettet BOOLEAN DEFAULT FALSE NOT NULL, --trenger vi den?
  opprettet_tid  TIMESTAMP(3) DEFAULT LOCALTIMESTAMP NOT NULL
)