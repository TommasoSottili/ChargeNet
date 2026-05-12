CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100)
    latitude DOUBLE
    longitude DOUBLE
    email VARCHAR(100) UNIQUE,
    password VARCHAR(255),
    role VARCHAR(50),
    wallet_balance DECIMAL(10, 2),
    discount DECIMAL(10, 2),
    connector_type VARCHAR(50),
    subscription_plan VARCHAR(50),
    battery_capacity DOUBLE,
    battery_percentage DOUBLE
    );

CREATE TABLE IF NOT EXISTS charging_stations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100),
    status VARCHAR(50),
    power_kw DOUBLE,
    tariff_operator DECIMAL(10, 2),
    tariff_platform DECIMAL(10, 2)
    );

CREATE TABLE IF NOT EXISTS charging_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    driver_id BIGINT,
    station_id BIGINT,
    strategy_used VARCHAR(50),
    battery_start DOUBLE,
    battery_current DOUBLE,
    kwh_delivered DOUBLE,
    cost_total DECIMAL(10, 2),
    status VARCHAR(50)
    );

CREATE TABLE IF NOT EXISTS transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    driver_id BIGINT,
    type VARCHAR(50),
    amount DECIMAL(10, 2),
    kwh DOUBLE,
    description VARCHAR(255)
    );