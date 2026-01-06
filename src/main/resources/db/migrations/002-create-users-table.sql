-- Создание таблицы users
CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    email VARCHAR UNIQUE NOT NULL,
    first_name VARCHAR,
    last_name VARCHAR,
    created_at TIMESTAMP,
    is_active BOOLEAN
);

-- Создание таблица accounts
CREATE TABLE accounts (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    account_type VARCHAR,
    balance DECIMAL,
    currency VARCHAR,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    is_active BOOLEAN,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Создание таблицы products
CREATE TABLE products (
    id BIGINT PRIMARY KEY,
    name VARCHAR,
    sku VARCHAR UNIQUE NOT NULL,
    price DECIMAL,
    stock_quantity INTEGER,
    status VARCHAR,
    created_at TIMESTAMP
);

-- Создание таблица orders
CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    payment_account_id BIGINT NOT NULL,
    total_amount DECIMAL,
    status VARCHAR,
    created_at TIMESTAMP,
    confirmed_at TIMESTAMP,
    shipped_at TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (payment_account_id) REFERENCES accounts(id)
);

-- Создание таблица transactions
CREATE TABLE transactions (
    id BIGINT PRIMARY KEY,
    from_account_id BIGINT NOT NULL,
    to_account_id BIGINT NOT NULL,
    amount DECIMAL,
    transaction_type VARCHAR,
    status VARCHAR,
    description VARCHAR,
    created_at TIMESTAMP,
    processed_at TIMESTAMP,
    metadata JSONB,
    FOREIGN KEY (from_account_id) REFERENCES accounts(id),
    FOREIGN KEY (to_account_id) REFERENCES accounts(id)
);

-- Создание таблица order_items
CREATE TABLE order_items (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER,
    unit_price DECIMAL,
    total_price DECIMAL,
    status VARCHAR,
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (product_id) REFERENCES products(id)
);