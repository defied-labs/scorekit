CREATE TABLE IF NOT EXISTS scorekit_numeric_scores (
    provider TEXT COLLATE "C" NOT NULL,
    leaderboard TEXT COLLATE "C" NOT NULL,
    entry_key TEXT COLLATE "C" NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    version BIGINT NOT NULL,
    removed BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (provider, leaderboard, entry_key)
);

CREATE INDEX IF NOT EXISTS scorekit_numeric_scores_ranking
    ON scorekit_numeric_scores (provider, leaderboard, score, entry_key) WHERE NOT removed;

CREATE INDEX IF NOT EXISTS scorekit_numeric_scores_changes
    ON scorekit_numeric_scores (provider, leaderboard, version);

CREATE TABLE IF NOT EXISTS scorekit_text_scores (
    provider TEXT COLLATE "C" NOT NULL,
    leaderboard TEXT COLLATE "C" NOT NULL,
    entry_key TEXT COLLATE "C" NOT NULL,
    score TEXT COLLATE "C" NOT NULL,
    version BIGINT NOT NULL,
    removed BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (provider, leaderboard, entry_key)
);

CREATE INDEX IF NOT EXISTS scorekit_text_scores_ranking
    ON scorekit_text_scores (provider, leaderboard, score, entry_key) WHERE NOT removed;

CREATE INDEX IF NOT EXISTS scorekit_text_scores_changes
    ON scorekit_text_scores (provider, leaderboard, version);
