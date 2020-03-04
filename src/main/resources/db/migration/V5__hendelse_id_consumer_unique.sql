ALTER TABLE hendelseslogg
    drop constraint hendelseslogg_hendelse_id_key;

ALTER TABLE hendelseslogg
    ADD CONSTRAINT hendelseslogg_hendelse_id_consumer_key UNIQUE (hendelse_id, consumer);