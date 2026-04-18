CREATE TABLE IF NOT EXISTS crea_foods (
    code        CHAR(6)       PRIMARY KEY,
    name_it     TEXT          NOT NULL,
    name_en     TEXT,
    name_sci    TEXT,
    category    TEXT,
    kcal        NUMERIC(7,2),
    protein_g   NUMERIC(7,2),
    carbs_g     NUMERIC(7,2),
    fat_g       NUMERIC(7,2),
    fiber_g     NUMERIC(7,2),
    water_g     NUMERIC(7,2),
    portion_g   NUMERIC(7,2),
    scraped_at  TIMESTAMPTZ   DEFAULT now()
);

CREATE TABLE IF NOT EXISTS yazio_foods (
    slug        TEXT          PRIMARY KEY,
    name_it     TEXT          NOT NULL,
    category    TEXT,
    kcal        NUMERIC(7,2),
    protein_g   NUMERIC(7,2),
    carbs_g     NUMERIC(7,2),
    fat_g       NUMERIC(7,2),
    scraped_at  TIMESTAMPTZ   DEFAULT now()
);
