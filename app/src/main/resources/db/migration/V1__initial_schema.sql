CREATE TABLE IF NOT EXISTS _migration_marker (
    id SERIAL PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    note TEXT
);

INSERT INTO _migration_marker (note) VALUES ('initial schema placeholder');