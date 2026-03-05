CREATE TABLE "orders" (
    "order_id" SERIAL PRIMARY KEY,
    "merchant_id" INT NOT NULL REFERENCES "merchants" ("merchant_id"),
    "cashier_id" INT NOT NULL REFERENCES "cashiers" ("cashier_id"),
    "total_price" BIGINT NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    "deleted_at" TIMESTAMP DEFAULT NULL
);

CREATE INDEX idx_orders_merchant_id ON orders (merchant_id);

CREATE INDEX idx_orders_cashier_id ON orders (cashier_id);

CREATE INDEX idx_orders_created_at ON orders (created_at);