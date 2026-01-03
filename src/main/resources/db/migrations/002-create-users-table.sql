-- src/main/resources/db/migrations/002-create-tables.sql
--liquibase formatted sql
--changeset mp161:create-tables
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone VARCHAR(20),
    city VARCHAR(100),
    registration_date TIMESTAMP,
    is_active BOOLEAN DEFAULT true,
    status VARCHAR(50)
);

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    total_amount DECIMAL(10,2),
    status VARCHAR(50),
    payment_method VARCHAR(50),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    delivery_address TEXT
);

CREATE TABLE categories (
    id SERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL
);

CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(500) NOT NULL,
    sku VARCHAR(100),
    price DECIMAL(10,2),
    category_id INT REFERENCES categories(id),
    stock_quantity INT,
    status VARCHAR(50),
    created_at TIMESTAMP,
    description TEXT,
    specifications JSONB
);
--rollback DROP TABLE order_items; DROP TABLE orders; DROP TABLE products; DROP TABLE categories; DROP TABLE users;