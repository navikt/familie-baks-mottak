ALTER TABLE hendelseslogg
    ADD COLUMN if not exists consumer varchar(30) NOT NULL default 'PDL';

CREATE INDEX ON hendelseslogg (consumer);

ALTER TABLE hendelseslogg
    ALTER COLUMN consumer DROP DEFAULT;

ALTER TABLE hendelseslogg
    ADD COLUMN if not exists metadata varchar(4000);

UPDATE hendelseslogg
SET metadata = CONCAT('aktørId=', aktor_id, E'\nendringsType=', endringstype, E'\nopplysningstypeType=', opplysningstype);

ALTER TABLE hendelseslogg
    drop column if exists aktor_id;

ALTER TABLE hendelseslogg
    drop column if exists opplysningstype;

ALTER TABLE hendelseslogg
    drop column if exists endringstype;
