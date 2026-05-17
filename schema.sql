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

CREATE TABLE IF NOT EXISTS curated_recipe_targets (
    target_id        TEXT          PRIMARY KEY,
    dish_query       TEXT          NOT NULL,
    display_name     TEXT          NOT NULL,
    meal_area        TEXT          NOT NULL,
    priority         INTEGER,
    crea_feasibility TEXT          NOT NULL,
    notes            TEXT,
    source_file      TEXT          NOT NULL,
    source_line      INTEGER,
    review_status    TEXT          NOT NULL DEFAULT 'pending',
    created_at       TIMESTAMPTZ   DEFAULT now(),
    updated_at       TIMESTAMPTZ   DEFAULT now(),

    CONSTRAINT curated_recipe_targets_meal_area_check
        CHECK (meal_area IN ('antipasto', 'primo', 'secondo', 'contorno', 'spuntino')),
    CONSTRAINT curated_recipe_targets_feasibility_check
        CHECK (crea_feasibility IN ('high', 'medium', 'low')),
    CONSTRAINT curated_recipe_targets_review_status_check
        CHECK (review_status IN ('pending', 'approved', 'rejected', 'needs_review'))
);

CREATE TABLE IF NOT EXISTS curated_recipe_target_crea_refs (
    target_id   TEXT        NOT NULL
                            REFERENCES curated_recipe_targets(target_id)
                            ON DELETE CASCADE,
    crea_code   CHAR(6)     NOT NULL
                            REFERENCES crea_foods(code),
    ref_order   INTEGER     NOT NULL,
    label       TEXT,
    role        TEXT,
    is_primary  BOOLEAN     NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ DEFAULT now(),

    PRIMARY KEY (target_id, crea_code),
    CONSTRAINT curated_recipe_target_crea_refs_order_check
        CHECK (ref_order > 0),
    CONSTRAINT curated_recipe_target_crea_refs_order_unique
        UNIQUE (target_id, ref_order)
);

CREATE INDEX IF NOT EXISTS idx_curated_recipe_targets_review_status
    ON curated_recipe_targets (review_status);

CREATE INDEX IF NOT EXISTS idx_curated_recipe_targets_meal_area
    ON curated_recipe_targets (meal_area);

CREATE INDEX IF NOT EXISTS idx_curated_recipe_targets_feasibility
    ON curated_recipe_targets (crea_feasibility);

CREATE INDEX IF NOT EXISTS idx_curated_recipe_targets_source_file
    ON curated_recipe_targets (source_file);

CREATE INDEX IF NOT EXISTS idx_curated_recipe_target_crea_refs_crea_code
    ON curated_recipe_target_crea_refs (crea_code);

CREATE INDEX IF NOT EXISTS idx_curated_recipe_target_crea_refs_target_id
    ON curated_recipe_target_crea_refs (target_id);

-- ============================================================
-- recipe_candidates: candidati ricette curate (v006)
-- ============================================================
CREATE TABLE IF NOT EXISTS recipe_candidates (
    candidate_id       TEXT PRIMARY KEY,
    target_id          TEXT REFERENCES curated_recipe_targets(target_id),
    dish_name          TEXT NOT NULL,
    declared_servings  INTEGER NOT NULL DEFAULT 1,
    computed_weight_g  NUMERIC(8,2),
    kcal_per_100g      NUMERIC(7,2),
    protein_per_100g   NUMERIC(7,2),
    carbs_per_100g     NUMERIC(7,2),
    fat_per_100g       NUMERIC(7,2),
    fiber_per_100g     NUMERIC(7,2),
    default_portion_g  NUMERIC(7,2),
    crea_coverage_pct  NUMERIC(5,2),
    confidence_level   TEXT CHECK (confidence_level IN ('high','medium','low')),
    source_ref         TEXT NOT NULL DEFAULT 'internal_curation_llm_assisted',
    extraction_method  TEXT NOT NULL DEFAULT 'llm_assisted',
    llm_model          TEXT,
    llm_prompt_version TEXT,
    review_status      TEXT NOT NULL DEFAULT 'draft'
                       CHECK (review_status IN ('draft','reviewable','approved','rejected')),
    quality_flags      TEXT,
    created_at         TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at         TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_recipe_candidates_target_id     ON recipe_candidates(target_id);
CREATE INDEX IF NOT EXISTS idx_recipe_candidates_review_status ON recipe_candidates(review_status);

-- ============================================================
-- recipe_ingredient_candidates: ingredienti per candidato (v006)
-- ============================================================
CREATE TABLE IF NOT EXISTS recipe_ingredient_candidates (
    id                    SERIAL PRIMARY KEY,
    candidate_id          TEXT NOT NULL REFERENCES recipe_candidates(candidate_id) ON DELETE CASCADE,
    crea_code             TEXT REFERENCES crea_foods(code),
    ingredient_name_raw   TEXT NOT NULL,
    grams_raw             NUMERIC(7,2),
    grams_normalized      NUMERIC(7,2),
    yield_factor          NUMERIC(6,3) NOT NULL DEFAULT 1.000,
    weight_contribution_g NUMERIC(7,2),
    kcal_contribution     NUMERIC(7,2),
    match_method          TEXT,
    match_confidence      NUMERIC(5,3),
    role                  TEXT DEFAULT 'main',
    sort_order            INTEGER NOT NULL DEFAULT 0,
    created_at            TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_recipe_ingredient_candidates_candidate ON recipe_ingredient_candidates(candidate_id);
CREATE INDEX IF NOT EXISTS idx_recipe_ingredient_candidates_crea      ON recipe_ingredient_candidates(crea_code);
