create table auth_user (
    id bigint auto_increment primary key,
    username varchar(100) not null unique,
    password_hash varchar(255) not null,
    role varchar(50) not null,
    merchant_id bigint not null,
    status varchar(20) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp
);
