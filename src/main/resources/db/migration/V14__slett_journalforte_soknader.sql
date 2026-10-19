-- Søknader beholdes kun så lenge en journalføringstask som ikke er ferdig eller avvikshåndtert trenger dem
DELETE FROM soknad_vedlegg v
WHERE NOT EXISTS (SELECT 1
                  FROM task t
                  WHERE t.type = 'journalførSøknad'
                    AND t.status NOT IN ('FERDIG', 'AVVIKSHÅNDTERT')
                    AND t.payload = v.soknad_id::text);

DELETE FROM soknad s
WHERE NOT EXISTS (SELECT 1
                  FROM task t
                  WHERE t.type = 'journalførSøknad'
                    AND t.status NOT IN ('FERDIG', 'AVVIKSHÅNDTERT')
                    AND t.payload = s.id::text);

DELETE FROM kontantstotte_soknad_vedlegg v
WHERE NOT EXISTS (SELECT 1
                  FROM task t
                  WHERE t.type = 'journalførKontantstøtteSøknad'
                    AND t.status NOT IN ('FERDIG', 'AVVIKSHÅNDTERT')
                    AND t.payload = v.soknad_id::text);

DELETE FROM kontantstotte_soknad s
WHERE NOT EXISTS (SELECT 1
                  FROM task t
                  WHERE t.type = 'journalførKontantstøtteSøknad'
                    AND t.status NOT IN ('FERDIG', 'AVVIKSHÅNDTERT')
                    AND t.payload = s.id::text);

ALTER TABLE soknad DROP COLUMN journalpost_id;
ALTER TABLE kontantstotte_soknad DROP COLUMN journalpost_id;
