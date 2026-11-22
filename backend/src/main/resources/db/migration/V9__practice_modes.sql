ALTER TABLE practice_sessions
 ADD COLUMN mode VARCHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'TEXT',
 ADD COLUMN initial_mode VARCHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'TEXT',
 ADD CONSTRAINT ck_practice_mode CHECK (mode IN ('TEXT', 'VOICE')),
 ADD CONSTRAINT ck_practice_initial_mode CHECK (initial_mode IN ('TEXT', 'VOICE')),
 ADD CONSTRAINT ck_practice_mode_transition CHECK (mode = 'TEXT' OR initial_mode = 'VOICE');
