-- H2 Database Schema for IMC Accident MCP Server

-- Create customers table
CREATE TABLE IF NOT EXISTS customers (
    customer_id INTEGER PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20),
    address VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(50),
    zip_code VARCHAR(10)
);

-- Create accidents table
CREATE TABLE IF NOT EXISTS accidents (
    accident_id INTEGER PRIMARY KEY,
    policy_id INTEGER NOT NULL,
    vehicle_id INTEGER NOT NULL,
    driver_id INTEGER NOT NULL,
    accident_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    latitude DECIMAL(10,8),
    longitude DECIMAL(11,8),
    g_force DECIMAL(5,2),
    description TEXT
);