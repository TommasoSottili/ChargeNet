CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) UNIQUE,
    password VARCHAR(255),
    role VARCHAR(50),
    wallet_balance DOUBLE,
    discount DOUBLE
    );

CREATE TABLE IF NOT EXISTS charging_stations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100),
    status VARCHAR(50),
    power_kw DOUBLE,
    tariff_operator DOUBLE,
    tariff_platform DOUBLE
    );

CREATE TABLE IF NOT EXISTS charging_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    driver_id BIGINT,
    station_id BIGINT,
    strategy_used VARCHAR(50),
    battery_start DOUBLE,
    battery_current DOUBLE,
    kwh_delivered DOUBLE,
    cost_total DOUBLE,
    status VARCHAR(50)
    );

CREATE TABLE IF NOT EXISTS transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    driver_id BIGINT,
    type VARCHAR(50),
    amount DOUBLE,
    kwh DOUBLE,
    description VARCHAR(255)
    );