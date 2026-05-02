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

CREATE TABLE IF NOT EXISTS dish_ambiguity_rules (
    rule_id            TEXT          PRIMARY KEY,
    target_kind        TEXT          NOT NULL,    -- 'category' | 'food_id'
    target_value       TEXT          NOT NULL,
    threshold_ratio    NUMERIC(4,2)  NOT NULL,    -- 0.00 = applica sempre
    correction_factor  NUMERIC(4,2)  NOT NULL,
    range_min_factor   NUMERIC(4,2)  NOT NULL,
    range_max_factor   NUMERIC(4,2)  NOT NULL,
    confidence_penalty SMALLINT      NOT NULL,    -- 0 | 1 | 2
    note_template      TEXT          NOT NULL,
    source_ref         TEXT,
    curated_at         TIMESTAMPTZ   DEFAULT now()
);

CREATE TABLE IF NOT EXISTS meal_context_corrections (
    context            TEXT          PRIMARY KEY, -- home|canteen|restaurant|social
    kcal_multiplier    NUMERIC(4,2)  NOT NULL,
    flat_extra_per_meal NUMERIC(6,2) NOT NULL,
    range_widen_pct    NUMERIC(4,2)  NOT NULL,
    confidence_penalty SMALLINT      NOT NULL,
    note_template      TEXT          NOT NULL,
    source_ref         TEXT,
    curated_at         TIMESTAMPTZ   DEFAULT now()
);
