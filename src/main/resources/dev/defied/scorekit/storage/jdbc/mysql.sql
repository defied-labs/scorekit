CREATE TABLE IF NOT EXISTS scorekit_numeric_scores (
    provider VARCHAR(191) NOT NULL,
    leaderboard VARCHAR(191) NOT NULL,
    entry_key VARCHAR(191) NOT NULL,
    score DOUBLE NOT NULL,
    version BIGINT NOT NULL,
    removed BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (provider, leaderboard, entry_key),
    INDEX scorekit_numeric_scores_ranking (provider, leaderboard, removed, score, entry_key),
    INDEX scorekit_numeric_scores_changes (provider, leaderboard, version)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS scorekit_text_scores (
    provider VARCHAR(191) NOT NULL,
    leaderboard VARCHAR(191) NOT NULL,
    entry_key VARCHAR(191) NOT NULL,
    score VARCHAR(191) NOT NULL,
    version BIGINT NOT NULL,
    removed BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (provider, leaderboard, entry_key),
    INDEX scorekit_text_scores_ranking (provider, leaderboard, removed, score, entry_key),
    INDEX scorekit_text_scores_changes (provider, leaderboard, version)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;