ALTER TABLE hendelseslogg
    ADD COLUMN consumer varchar(30) NOT NULL default 'LEESAH';

CREATE INDEX ON hendelseslogg (consumer);

ALTER TABLE hendelseslogg
    ALTER COLUMN consumer DROP DEFAULT;

ALTER TABLE hendelseslogg
    ADD COLUMN metadata varchar(4000);

UPDATE hendelseslogg
SET metadata = CONCAT('aktørId=', aktor_id, E'\nendringsType=', endringstype);
