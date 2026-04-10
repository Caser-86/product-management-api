CREATE TABLE storefront_product_search (
    product_id BIGINT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    primary_sku_id BIGINT NOT NULL,
    min_price DECIMAL(18,2) NOT NULL,
    max_price DECIMAL(18,2) NOT NULL,
    available_qty INT NOT NULL,
    stock_status VARCHAR(20) NOT NULL,
    product_status VARCHAR(20) NOT NULL,
    publish_status VARCHAR(20) NOT NULL,
    audit_status VARCHAR(20) NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_storefront_product_search_visibility
    ON storefront_product_search (product_status, publish_status, audit_status, category_id);
