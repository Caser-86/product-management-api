ALTER TABLE product_spu ADD COLUMN audit_comment VARCHAR(500) NULL;
ALTER TABLE product_spu ADD COLUMN audit_by BIGINT NULL;
ALTER TABLE product_spu ADD COLUMN audit_at DATETIME NULL;
ALTER TABLE product_spu ADD COLUMN submitted_at DATETIME NULL;
ALTER TABLE product_spu ADD COLUMN published_at DATETIME NULL;
ALTER TABLE product_spu ADD COLUMN published_by BIGINT NULL;

CREATE TABLE product_workflow_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    from_status VARCHAR(20) NOT NULL,
    to_status VARCHAR(20) NOT NULL,
    from_audit_status VARCHAR(20) NOT NULL,
    to_audit_status VARCHAR(20) NOT NULL,
    from_publish_status VARCHAR(20) NOT NULL,
    to_publish_status VARCHAR(20) NOT NULL,
    operator_id BIGINT NULL,
    operator_role VARCHAR(32) NULL,
    comment VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_workflow_history_product FOREIGN KEY (product_id) REFERENCES product_spu(id)
);

CREATE INDEX idx_workflow_history_product_created
    ON product_workflow_history (product_id, created_at);
