insert into auth_user (username, password_hash, role, merchant_id, status)
values
    ('platform-admin', '$2a$10$1EFUc8TJe7ds2rCwgvaAMOVUk9aK3rQKwimoP2SLCDrcpbD1kuQ06', 'PLATFORM_ADMIN', 2001, 'active'),
    ('merchant-admin', '$2a$10$.oOjX/t/OrDHZv/YV1Z7Huv4ElrKj38tWbBd4et5hCXR24M17DTMm', 'MERCHANT_ADMIN', 2001, 'active');
