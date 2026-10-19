-- b4-datalifecycle: IDCAMS DEFINE GENERATIONDATAGROUP catalog (DEFGDGB, DEFGDGD, REPTFILE).
-- Generations themselves live in the GenerationStore port; this table keeps the GDG base
-- definitions (LIMIT, SCRATCH) that the frozen port cannot express.
CREATE TABLE dl_gdg_base (
    base_name VARCHAR(44) NOT NULL,
    gen_limit INTEGER NOT NULL,
    scratch BOOLEAN NOT NULL DEFAULT TRUE,
    defined_at VARCHAR(26) NOT NULL,
    CONSTRAINT pk_dl_gdg_base PRIMARY KEY (base_name),
    CONSTRAINT ck_dl_gdg_limit CHECK (gen_limit BETWEEN 1 AND 255)
);
