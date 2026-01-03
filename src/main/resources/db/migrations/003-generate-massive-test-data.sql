-- Генерация 100,000 пользователей с реалистичными данными
INSERT INTO users (email, first_name, last_name, phone, city, registration_date, is_active, status)
SELECT
    'user' || generate_series || '@domain' || (generate_series % 10) || '.com',
    'FirstName' || generate_series,
    'LastName' || generate_series,
    '+7' || LPAD((9000000000 + generate_series)::text, 10, '0'),
    CASE (generate_series % 20)
        WHEN 0 THEN 'Moscow'
        WHEN 1 THEN 'Saint Petersburg'
        WHEN 2 THEN 'Novosibirsk'
        WHEN 3 THEN 'Ekaterinburg'
        WHEN 4 THEN 'Kazan'
        WHEN 5 THEN 'Nizhny Novgorod'
        WHEN 6 THEN 'Chelyabinsk'
        WHEN 7 THEN 'Samara'
        WHEN 8 THEN 'Omsk'
        WHEN 9 THEN 'Rostov-on-Don'
        WHEN 10 THEN 'Ufa'
        WHEN 11 THEN 'Krasnoyarsk'
        WHEN 12 THEN 'Voronezh'
        WHEN 13 THEN 'Perm'
        WHEN 14 THEN 'Volgograd'
        WHEN 15 THEN 'Krasnodar'
        WHEN 16 THEN 'Saratov'
        WHEN 17 THEN 'Tyumen'
        WHEN 18 THEN 'Tolyatti'
        ELSE 'Izhevsk'
    END,
    NOW() - (random() * interval '3 years'),
    random() > 0.05, -- 95% активных
    CASE
        WHEN random() > 0.1 THEN 'ACTIVE'
        WHEN random() > 0.02 THEN 'SUSPENDED'
        ELSE 'BLOCKED'
    END
FROM generate_series(1, 100000);

-- Генерация 300,000 заказов с различными статусами
INSERT INTO orders (user_id, total_amount, status, payment_method, created_at, updated_at, delivery_address)
SELECT
    (random() * 100000 + 1)::bigint,
    (random() * 5000 + 50)::decimal(10,2),
    CASE (random() * 6)::int
        WHEN 0 THEN 'PENDING'
        WHEN 1 THEN 'CONFIRMED'
        WHEN 2 THEN 'PROCESSING'
        WHEN 3 THEN 'SHIPPED'
        WHEN 4 THEN 'DELIVERED'
        ELSE 'CANCELLED'
    END,
    CASE (random() * 4)::int
        WHEN 0 THEN 'CREDIT_CARD'
        WHEN 1 THEN 'DEBIT_CARD'
        WHEN 2 THEN 'PAYPAL'
        ELSE 'BANK_TRANSFER'
    END,
    NOW() - (random() * interval '2 years'),
    NOW() - (random() * interval '1 year'),
    'Address ' || generate_series || ', City, Country'
FROM generate_series(1, 300000);

-- Генерация 50,000 товаров с JSON данными для демонстрации GIN
INSERT INTO products (name, sku, price, category_id, stock_quantity, status, created_at, description, specifications)
SELECT
    CASE (generate_series % 10)
        WHEN 0 THEN 'Смартфон Apple iPhone'
        WHEN 1 THEN 'Ноутбук Lenovo ThinkPad'
        WHEN 2 THEN 'Планшет Samsung Galaxy Tab'
        WHEN 3 THEN 'Наушники Sony WH-1000XM'
        WHEN 4 THEN 'Умные часы Apple Watch'
        WHEN 5 THEN 'Игровая консоль PlayStation'
        WHEN 6 THEN 'Телевизор LG OLED'
        WHEN 7 THEN 'Фотоаппарат Canon EOS'
        WHEN 8 THEN 'Колонка JBL Charge'
        ELSE 'Роутер TP-Link Archer'
    END || ' ' || generate_series,
    'SKU-' || LPAD(generate_series::text, 8, '0'),
    (random() * 2000 + 10)::decimal(10,2),
    (random() * 10 + 1)::bigint,
    (random() * 1000)::int,
    CASE
        WHEN random() > 0.2 THEN 'ACTIVE'
        WHEN random() > 0.05 THEN 'OUT_OF_STOCK'
        ELSE 'DISCONTINUED'
    END,
    NOW() - (random() * interval '1 year'),
    'Детальное описание товара ' || generate_series || ' с множеством характеристик и преимуществ. Высокое качество, надежность, гарантия производителя.',
    jsonb_build_object(
        'brand', CASE (generate_series % 5)
            WHEN 0 THEN 'Apple'
            WHEN 1 THEN 'Samsung'
            WHEN 2 THEN 'Sony'
            WHEN 3 THEN 'LG'
            ELSE 'Xiaomi'
        END,
        'color', CASE (generate_series % 4)
            WHEN 0 THEN 'black'
            WHEN 1 THEN 'white'
            WHEN 2 THEN 'silver'
            ELSE 'gold'
        END,
        'weight', (random() * 2000 + 100)::int,
        'features', ARRAY['waterproof', 'wireless', 'fast-charging', 'hd-display'],
        'warranty_years', (random() * 3 + 1)::int,
        'energy_rating', CASE (generate_series % 5)
            WHEN 0 THEN 'A++'
            WHEN 1 THEN 'A+'
            WHEN 2 THEN 'A'
            WHEN 3 THEN 'B'
            ELSE 'C'
        END
    )
FROM generate_series(1, 50000);

-- Демонстрация различных типов индексов
-- 1. B-Tree индексы (основной фокус урока)
CREATE INDEX idx_products_price_btree ON products(price);
CREATE INDEX idx_products_category_status ON products(category_id, status);

-- 2. GIN индекс для полнотекстового поиска
CREATE INDEX idx_products_description_gin ON products USING gin(to_tsvector('russian', description));

-- 3. GIN индекс для JSON данных
CREATE INDEX idx_products_specifications_gin ON products USING gin(specifications);

-- 4. GIN индекс для подстрок (требует расширение pg_trgm)
-- CREATE EXTENSION IF NOT EXISTS pg_trgm;
-- CREATE INDEX idx_products_name_trgm ON products USING gin(name gin_trgm_ops);

-- 5. Функциональный B-Tree индекс
CREATE INDEX idx_products_name_lower ON products(LOWER(name));

-- 6. Частичный B-Tree индекс
CREATE INDEX idx_products_active_price ON products(price) WHERE status = 'ACTIVE';

-- Примеры запросов для демонстрации разных индексов:

-- B-Tree: диапазон цен
-- EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM products WHERE price BETWEEN 500 AND 1000;

-- GIN: полнотекстовый поиск
-- EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM products WHERE to_tsvector('russian', description) @@ to_tsquery('russian', 'смартфон');

-- GIN: JSON запросы
-- EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM products WHERE specifications->>'brand' = 'Apple';
-- EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM products WHERE specifications->'features' ? 'waterproof';

-- Функциональный индекс
-- EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM products WHERE LOWER(name) = 'смартфон apple iphone 1000';

-- Частичный индекс
-- EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM products WHERE status = 'ACTIVE' AND price > 1000;

-- Обновляем статистику для точного планирования
ANALYZE users;
ANALYZE orders;
ANALYZE products;
ANALYZE categories;