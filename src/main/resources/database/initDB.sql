CREATE TABLE IF NOT EXISTS devices
(
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    mac_address VARCHAR(20) NOT NULL UNIQUE,
    host_index INT,
    rule_index INT
);