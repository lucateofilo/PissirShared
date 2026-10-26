-- Schema del database della centrale operativa.
-- Viene eseguito da Postgres al primo avvio del container.

CREATE TABLE station (
    id             VARCHAR(10) PRIMARY KEY,
    name           VARCHAR(100) NOT NULL,
    state          VARCHAR(20)  NOT NULL DEFAULT 'OFFLINE',
    last_heartbeat TIMESTAMPTZ
);

CREATE TABLE train (
    id           VARCHAR(10) PRIMARY KEY,
    state        VARCHAR(20) NOT NULL DEFAULT 'IN_STATION',
    direction    CHAR(1) CHECK (direction IN ('A', 'R')),
    last_station VARCHAR(10) REFERENCES station (id),
    next_station VARCHAR(10) REFERENCES station (id),
    journey_start    TIMESTAMPTZ,              -- partenza dalla stazione di origine del viaggio in corso
    expected_arrival TIMESTAMPTZ,              -- arrivo stimato alla prossima stazione
    delay_sec        INTEGER NOT NULL DEFAULT 0
);

-- Una tratta è il viaggio di un treno in una direzione (A = andata, R = ritorno)
CREATE TABLE route (
    id        SERIAL PRIMARY KEY,
    train_id  VARCHAR(10) NOT NULL REFERENCES train (id) ON DELETE CASCADE,
    direction CHAR(1)     NOT NULL CHECK (direction IN ('A', 'R')),
    UNIQUE (train_id, direction)
);

-- Fermate di una tratta, in ordine (seq). travel_time_sec è il tempo dalla fermata precedente.
CREATE TABLE route_stop (
    route_id        INTEGER     NOT NULL REFERENCES route (id) ON DELETE CASCADE,
    seq             INTEGER     NOT NULL,
    station_id      VARCHAR(10) NOT NULL REFERENCES station (id),
    travel_time_sec INTEGER     NOT NULL DEFAULT 0,
    dwell_sec       INTEGER     NOT NULL DEFAULT 0,
    PRIMARY KEY (route_id, seq)
);

CREATE TABLE transit (
    event_id     UUID PRIMARY KEY,
    train_id     VARCHAR(10) NOT NULL REFERENCES train (id),
    station_id   VARCHAR(10) NOT NULL REFERENCES station (id),
    type         VARCHAR(5)  NOT NULL CHECK (type IN ('ENTRY', 'EXIT')),
    ts           TIMESTAMPTZ NOT NULL,
    scheduled_ts TIMESTAMPTZ,
    delay_sec    INTEGER
);

CREATE TABLE fault (
    id            SERIAL PRIMARY KEY,
    station_id    VARCHAR(10) NOT NULL REFERENCES station (id),
    kind          VARCHAR(30) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'DISPATCHED', 'RESOLVED')),
    opened_at     TIMESTAMPTZ NOT NULL,
    dispatched_at TIMESTAMPTZ,
    resolved_at   TIMESTAMPTZ,
    dispatched_by VARCHAR(50)
);

CREATE TABLE alert (
    id       SERIAL PRIMARY KEY,
    type     VARCHAR(30)  NOT NULL,
    severity VARCHAR(10)  NOT NULL,
    ref      VARCHAR(50),
    message  VARCHAR(255) NOT NULL,
    ts       TIMESTAMPTZ  NOT NULL,
    acked    BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE app_user (
    username      VARCHAR(50) PRIMARY KEY,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(10)  NOT NULL CHECK (role IN ('TECH', 'ADMIN'))
);
