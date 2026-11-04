-- Esquema de Marcador 26

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(80)  NOT NULL,
    email         VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(10)  NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE matches (
    id          BIGSERIAL PRIMARY KEY,
    stage       VARCHAR(40)  NOT NULL,
    home_team   VARCHAR(60)  NOT NULL,
    away_team   VARCHAR(60)  NOT NULL,
    home_code   VARCHAR(3)   NOT NULL,
    away_code   VARCHAR(3)   NOT NULL,
    venue       VARCHAR(80),
    kickoff_at  TIMESTAMPTZ  NOT NULL,
    status      VARCHAR(10)  NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'FINISHED')),
    home_goals  INT CHECK (home_goals >= 0),
    away_goals  INT CHECK (away_goals >= 0),
    finished_at TIMESTAMPTZ
);
CREATE INDEX idx_matches_kickoff ON matches (kickoff_at);
CREATE INDEX idx_matches_status ON matches (status);

CREATE TABLE predictions (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    match_id     BIGINT      NOT NULL REFERENCES matches (id) ON DELETE CASCADE,
    home_goals   INT         NOT NULL CHECK (home_goals BETWEEN 0 AND 30),
    away_goals   INT         NOT NULL CHECK (away_goals BETWEEN 0 AND 30),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- última vez que se registró/editó: es la hora que cuenta para la regla 5
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_prediction UNIQUE (user_id, match_id)
);
CREATE INDEX idx_predictions_match ON predictions (match_id);

-- Desglose de puntos por participante y partido (lo escribe el worker)
CREATE TABLE prediction_scores (
    user_id      BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    match_id     BIGINT      NOT NULL REFERENCES matches (id) ON DELETE CASCADE,
    rule         VARCHAR(15) NOT NULL,
    base_points  INT         NOT NULL,
    winner_hit   BOOLEAN     NOT NULL,
    goal_diff_hit BOOLEAN    NOT NULL,
    early        BOOLEAN     NOT NULL,
    last_minute  BOOLEAN     NOT NULL,
    early_bonus  INT         NOT NULL,
    streak_bonus INT         NOT NULL,
    streak_after INT         NOT NULL,
    total        INT         NOT NULL,
    PRIMARY KEY (user_id, match_id)
);

-- Totales por participante (lo escribe el worker)
CREATE TABLE user_scores (
    user_id         BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    total_points    INT NOT NULL DEFAULT 0,
    predicted_count INT NOT NULL DEFAULT 0,
    exact_hits      INT NOT NULL DEFAULT 0,
    winner_hits     INT NOT NULL DEFAULT 0,
    current_streak  INT NOT NULL DEFAULT 0,
    best_streak     INT NOT NULL DEFAULT 0,
    early_points    INT NOT NULL DEFAULT 0,
    streak_points   INT NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_scores_total ON user_scores (total_points DESC, exact_hits DESC);

CREATE TABLE rooms (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(60)  NOT NULL,
    description VARCHAR(200),
    invite_code VARCHAR(12)  NOT NULL UNIQUE,
    owner_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE room_members (
    room_id   BIGINT      NOT NULL REFERENCES rooms (id) ON DELETE CASCADE,
    user_id   BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role      VARCHAR(10) NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('OWNER', 'MEMBER')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (room_id, user_id)
);
CREATE INDEX idx_room_members_user ON room_members (user_id);

-- Bitácora de recálculos: evidencia de qué réplica del worker procesó cada evento
CREATE TABLE scoring_runs (
    id              BIGSERIAL PRIMARY KEY,
    trigger_match   BIGINT,
    reason          VARCHAR(30) NOT NULL,
    instance        VARCHAR(80) NOT NULL,
    users_processed INT         NOT NULL,
    matches_processed INT       NOT NULL,
    duration_ms     BIGINT      NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
