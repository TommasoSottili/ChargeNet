-- ============================================================================
--  ChargeNet — Schema PostgreSQL
--  Idempotente: CREATE ... IF NOT EXISTS, nessun DROP. Lo schema si crea una
--  volta e i dati persistono tra i riavvii (seed condizionale lato Java).
-- ============================================================================

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    wallet_balance DECIMAL(10, 2) CHECK (wallet_balance IS NULL OR wallet_balance >= 0),
    connector_type VARCHAR(50),
    subscription_plan VARCHAR(50),
    battery_capacity DOUBLE PRECISION,
    total_earnings DECIMAL(10, 2) CHECK (total_earnings IS NULL OR total_earnings >= 0)
    );

CREATE TABLE IF NOT EXISTS power_transformers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    temperature DOUBLE PRECISION NOT NULL DEFAULT 25.0,
    load_percent DOUBLE PRECISION NOT NULL DEFAULT 0.0
    );

CREATE TABLE IF NOT EXISTS charging_stations (
    id BIGSERIAL PRIMARY KEY,
    operator_id BIGINT NOT NULL,
    transformer_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    connector_type VARCHAR(50) NOT NULL,
    power_kw DOUBLE PRECISION NOT NULL CHECK (power_kw > 0),
    is_solar_powered BOOLEAN NOT NULL DEFAULT FALSE,
    tariff_operator DECIMAL(10, 2) NOT NULL CHECK (tariff_operator >= 0),
    tariff_platform DECIMAL(10, 2) NOT NULL DEFAULT 0.00 CHECK (tariff_platform >= 0),
    average_rating DOUBLE PRECISION DEFAULT 0.0 CHECK (average_rating BETWEEN 0 AND 5),
    total_ratings INTEGER DEFAULT 0 CHECK (total_ratings >= 0),
    status VARCHAR(50) NOT NULL,
    reserved_by_id BIGINT,
    expiration_timestamp TIMESTAMP,
    CONSTRAINT fk_operator FOREIGN KEY (operator_id) REFERENCES users(id),
    CONSTRAINT fk_transformer FOREIGN KEY (transformer_id) REFERENCES power_transformers(id),
    CONSTRAINT fk_reserved_by FOREIGN KEY (reserved_by_id) REFERENCES users(id)
    );

CREATE TABLE IF NOT EXISTS charging_sessions (
    id BIGSERIAL PRIMARY KEY,
    driver_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    strategy_used VARCHAR(50) NOT NULL,
    battery_start DOUBLE PRECISION NOT NULL,
    battery_current DOUBLE PRECISION NOT NULL,
    kwh_delivered DOUBLE PRECISION NOT NULL DEFAULT 0.0 CHECK (kwh_delivered >= 0),
    cost_total DECIMAL(10, 2) NOT NULL DEFAULT 0.00 CHECK (cost_total >= 0),
    status VARCHAR(50) NOT NULL,
    opened_at TIMESTAMP NOT NULL,
    closed_at TIMESTAMP,
    CONSTRAINT fk_session_driver FOREIGN KEY (driver_id) REFERENCES users(id),
    CONSTRAINT fk_session_station FOREIGN KEY (station_id) REFERENCES charging_stations(id)
    );

CREATE TABLE IF NOT EXISTS transactions (
    id BIGSERIAL PRIMARY KEY,
    driver_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    kwh DOUBLE PRECISION DEFAULT 0.0,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transaction_driver FOREIGN KEY (driver_id) REFERENCES users(id)
    );

CREATE TABLE IF NOT EXISTS ratings (
    id BIGSERIAL PRIMARY KEY,
    driver_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    stars INTEGER NOT NULL CHECK (stars >= 1 AND stars <= 5),
    comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rating_driver FOREIGN KEY (driver_id) REFERENCES users(id),
    CONSTRAINT fk_rating_station FOREIGN KEY (station_id) REFERENCES charging_stations(id),
    CONSTRAINT fk_rating_session FOREIGN KEY (session_id) REFERENCES charging_sessions(id),
    CONSTRAINT uq_driver_session UNIQUE (driver_id, session_id)
    );

CREATE TABLE IF NOT EXISTS rating_alerts (
    id BIGSERIAL PRIMARY KEY,
    station_id BIGINT NOT NULL,
    avg_at_creation DOUBLE PRECISION NOT NULL,
    status VARCHAR(50) NOT NULL,
    manager_note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_alert_station FOREIGN KEY (station_id) REFERENCES charging_stations(id)
    );