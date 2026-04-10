CREATE TABLE price_current (
    sku_id BIGINT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    list_price DECIMAL(18,2) NOT NULL,
    sale_price DECIMAL(18,2) NOT NULL,
    cost_price DECIMAL(18,2) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE price_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sku_id BIGINT NOT NULL,
    merchant_id BIGINT NOT NULL,
    change_type VARCHAR(20) NOT NULL,
    old_price_json JSON NOT NULL,
    new_price_json JSON NOT NULL,
    reason VARCHAR(255) NULL,
    operator_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
