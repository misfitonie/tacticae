CREATE TABLE factions (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bsdata_id  VARCHAR(255) NOT NULL UNIQUE,
    name       VARCHAR(255) NOT NULL
);

CREATE TABLE unit_profiles (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bsdata_id  VARCHAR(255) NOT NULL UNIQUE,
    name       VARCHAR(255) NOT NULL,
    faction_id UUID NOT NULL REFERENCES factions(id),
    toughness  SMALLINT NOT NULL,
    wounds     SMALLINT NOT NULL,
    save       SMALLINT NOT NULL
);

CREATE TABLE weapon_profiles (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bsdata_id  VARCHAR(255) NOT NULL UNIQUE,
    name       VARCHAR(255) NOT NULL,
    unit_id    UUID NOT NULL REFERENCES unit_profiles(id),
    attacks    VARCHAR(16) NOT NULL,
    skill      SMALLINT NOT NULL,
    strength   SMALLINT NOT NULL,
    ap         SMALLINT NOT NULL DEFAULT 0,
    damage     VARCHAR(16) NOT NULL,
    keywords   TEXT
);
