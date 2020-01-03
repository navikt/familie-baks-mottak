CREATE TABLE hendelseslogg (
    id              bigint                                                 NOT NULL PRIMARY KEY,
    kafka_offset    bigint                                                 NOT NULL,
    hendelse_id     varchar(50)                                            UNIQUE NOT NULL,
    aktor_id        varchar(30)                                            NOT NULL,
    opprettet_tid   timestamp(3) DEFAULT LOCALTIMESTAMP                    NOT NULL,
    opplysningstype varchar(30)                                            NOT NULL,
    endringstype    varchar(30)                                            NOT NULL
);

CREATE SEQUENCE hendelseslogg_seq INCREMENT BY 50 START WITH 1000000 NO CYCLE;

CREATE INDEX ON hendelseslogg (hendelse_id);