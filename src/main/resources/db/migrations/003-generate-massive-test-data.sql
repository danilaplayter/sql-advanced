-- Добавляем данные в таблицу отзывов
INSERT INTO reviews (user_id, product_id, rating, review_text, created_at, is_verified_purchase)
SELECT
    (random() * 50000 + 1)::bigint,
    (random() * 10000 + 1)::bigint,
    (random() * 4 + 1)::int,
    'Отзыв #' || generate_series || ' о товаре с подробной оценкой качества и доставки',
    NOW() - (random() * interval '2 years'),
    random() > 0.3
FROM generate_series(1, 200000);

-- Добавляем категории товаров
INSERT INTO categories (name, parent_id, is_active) VALUES
('Electronics', NULL, true),
('Smartphones', 1, true),
('Laptops', 1, true),
('Clothing', NULL, true),
('Books', NULL, true),
('Sports', NULL, true),
('Home & Garden', NULL, true);

-- Обновляем пользователей с регионами и статусами
UPDATE users SET
    region = CASE (id % 8)
        WHEN 0 THEN 'MOSCOW'
        WHEN 1 THEN 'SPB'
        WHEN 2 THEN 'EKATERINBURG'
        WHEN 3 THEN 'NOVOSIBIRSK'
        WHEN 4 THEN 'KAZAN'
        WHEN 5 THEN 'ROSTOV'
        WHEN 6 THEN 'UFA'
        ELSE 'SAMARA'
    END,
    account_status = CASE (id % 4)
        WHEN 0 THEN 'ACTIVE'
        WHEN 1 THEN 'INACTIVE'
        WHEN 2 THEN 'PENDING'
        ELSE 'SUSPENDED'
    END,
    registration_date = NOW() - (random() * interval '3 years');

-- Добавляем больше товаров
INSERT INTO products (name, description, price, category_id, is_active, created_at)
SELECT
    'Товар #' || generate_series,
    'Описание товара ' || generate_series || ' с подробными характеристиками',
    (random() * 50000 + 100)::decimal(10,2),
    (random() * 7 + 1)::bigint,
    random() > 0.1,
    NOW() - (random() * interval '1 year')
FROM generate_series(50001, 100000);

ANALYZE reviews;
ANALYZE categories;
ANALYZE users;
ANALYZE products;