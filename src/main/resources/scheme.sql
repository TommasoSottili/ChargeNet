DROP TABLE IF EXISTS rating_alerts CASCADE;
DROP TABLE IF EXISTS ratings CASCADE;
DROP TABLE IF EXISTS transactions CASCADE;
DROP TABLE IF EXISTS charging_sessions CASCADE;
DROP TABLE IF EXISTS charging_stations CASCADE;
DROP TABLE IF EXISTS power_transformers CASCADE;
DROP TABLE IF EXISTS users CASCADE;

CREATE TABLE users (
id BIGSERIAL PRIMARY KEY,
name VARCHAR(255) NOT NULL,
email VARCHAR(255) UNIQUE NOT NULL,
password VARCHAR(255) NOT NULL,
role VARCHAR(50) NOT NULL,
latitude DOUBLE PRECISION,
longitude DOUBLE PRECISION,
wallet_balance DECIMAL(10, 2),
connector_type VARCHAR(50),
subscription_plan VARCHAR(50),
battery_capacity DOUBLE PRECISION,
total_earnings DECIMAL(10, 2)
);

CREATE TABLE power_transformers (
id BIGSERIAL PRIMARY KEY,
name VARCHAR(255) NOT NULL,
temperature DOUBLE PRECISION NOT NULL DEFAULT 25.0,
load_percent DOUBLE PRECISION NOT NULL DEFAULT 0.0
);

CREATE TABLE charging_stations (
id BIGSERIAL PRIMARY KEY,
operator_id BIGINT NOT NULL,
transformer_id BIGINT NOT NULL,
name VARCHAR(255) NOT NULL,
address VARCHAR(255) NOT NULL,
latitude DOUBLE PRECISION NOT NULL,
longitude DOUBLE PRECISION NOT NULL,
connector_type VARCHAR(50) NOT NULL,
power_kw DOUBLE PRECISION NOT NULL,
is_solar_powered BOOLEAN NOT NULL DEFAULT FALSE,
tariff_operator DECIMAL(10, 2) NOT NULL,
tariff_platform DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
average_rating DOUBLE PRECISION DEFAULT 0.0,
total_ratings INTEGER DEFAULT 0,
status VARCHAR(50) NOT NULL,
reserved_by_id BIGINT,
expiration_timestamp TIMESTAMP,
CONSTRAINT fk_operator FOREIGN KEY (operator_id) REFERENCES users(id),
CONSTRAINT fk_transformer FOREIGN KEY (transformer_id) REFERENCES power_transformers(id),
CONSTRAINT fk_reserved_by FOREIGN KEY (reserved_by_id) REFERENCES users(id)
);

CREATE TABLE charging_sessions (
id BIGSERIAL PRIMARY KEY,
driver_id BIGINT NOT NULL,
station_id BIGINT NOT NULL,
strategy_used VARCHAR(50) NOT NULL,
battery_start DOUBLE PRECISION NOT NULL,
battery_current DOUBLE PRECISION NOT NULL,
kwh_delivered DOUBLE PRECISION NOT NULL DEFAULT 0.0,
cost_total DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
status VARCHAR(50) NOT NULL,
opened_at TIMESTAMP NOT NULL,
closed_at TIMESTAMP,
CONSTRAINT fk_session_driver FOREIGN KEY (driver_id) REFERENCES users(id),
CONSTRAINT fk_session_station FOREIGN KEY (station_id) REFERENCES charging_stations(id)
);

CREATE TABLE transactions (
id BIGSERIAL PRIMARY KEY,
driver_id BIGINT NOT NULL,
type VARCHAR(50) NOT NULL,
amount DECIMAL(10, 2) NOT NULL,
kwh DOUBLE PRECISION DEFAULT 0.0,
description VARCHAR(255) NOT NULL,
created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
CONSTRAINT fk_transaction_driver FOREIGN KEY (driver_id) REFERENCES users(id)
);

CREATE TABLE ratings (
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

 CREATE TABLE rating_alerts (
 id BIGSERIAL PRIMARY KEY,
 station_id BIGINT NOT NULL,
 avg_at_creation DOUBLE PRECISION NOT NULL,
 status VARCHAR(50) NOT NULL,
 manager_note TEXT,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_alert_station FOREIGN KEY (station_id) REFERENCES charging_stations(id)
 );

-- CREATE TABLE IF NOT EXISTS users (
--     id BIGINT AUTO_INCREMENT PRIMARY KEY,
--     name VARCHAR(100)
--     latitude DOUBLE
--     longitude DOUBLE
--     email VARCHAR(100) UNIQUE,
--     password VARCHAR(255),
--     role VARCHAR(50),
--     wallet_balance DECIMAL(10, 2),
--     connector_type VARCHAR(50),
--     subscription_plan VARCHAR(50),
--     battery_capacity DOUBLE,
--     battery_percentage DOUBLE,
--     total_earnings DECIMAL(10, 2)
--     );
--
-- CREATE TABLE IF NOT EXISTS charging_stations (
--     id SERIAL PRIMARY KEY, -- o BIGSERIAL se prevedi miliardi di record
--     operator_id BIGINT, -- Foreign key verso la tabella degli operatori
--     transformer_id BIGINT, -- Foreign key verso la tabella dei trasformatori
--     name VARCHAR(255) NOT NULL,
--     address VARCHAR(255) NOT NULL,
--     latitude DOUBLE PRECISION NOT NULL,
--     longitude DOUBLE PRECISION NOT NULL,
--     connector_type VARCHAR(50) NOT NULL, -- Enum salvato come Stringa (es. 'TYPE_2', 'CCS')
--     power_kw DOUBLE PRECISION NOT NULL,
--     is_solar_powered BOOLEAN NOT NULL DEFAULT FALSE,
--     tariff_operator DECIMAL(10, 2) NOT NULL,
--     tariff_platform DECIMAL(10, 2) NOT NULL,
--     average_rating DOUBLE PRECISION DEFAULT 0.0,
--     total_ratings INTEGER DEFAULT 0,
--     status VARCHAR(50) NOT NULL, -- Enum (es. 'ACTIVE', 'BUSY', 'OVERLOADED')
--     reserved_by_id BIGINT, -- Foreign key verso la tabella dei Driver (null se non è prenotata)
--     expiration_timestamp TIMESTAMP, -- Null se non è prenotata
--
-- -- Vincoli di integrità (Foreign Keys)
--
--    CONSTRAINT fk_operator FOREIGN KEY (operator_id) REFERENCES users(id),
--    CONSTRAINT fk_transformer FOREIGN KEY (transformer_id) REFERENCES power_transformers(id),
--    CONSTRAINT fk_reserved_by FOREIGN KEY (reserved_by_id) REFERENCES users(id)
--     );
--
-- CREATE TABLE IF NOT EXISTS charging_sessions (
--     id BIGINT AUTO_INCREMENT PRIMARY KEY,
--     driver_id BIGINT,
--     station_id BIGINT,
--     strategy_used VARCHAR(50),
--     battery_start DOUBLE,
--     battery_current DOUBLE,
--     kwh_delivered DOUBLE,
--     cost_total DECIMAL(10, 2),
--     status VARCHAR(50),
--     opened_at TIMESTAMP,
--     closed_at TIMESTAMP
--     );
--
-- CREATE TABLE IF NOT EXISTS transactions (
--     id BIGINT AUTO_INCREMENT PRIMARY KEY,
--     driver_id BIGINT,
--     type VARCHAR(50),
--     amount DECIMAL(10, 2),
--     kwh DOUBLE,
--     description VARCHAR(255)
--     );