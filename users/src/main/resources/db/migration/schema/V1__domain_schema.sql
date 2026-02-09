-- 0) user_auth
CREATE SCHEMA IF NOT EXISTS user_auth;

DO $$ BEGIN
  CREATE TYPE user_auth.user_role AS ENUM ('CUSTOMER','OWNER','MANAGER','ADMIN');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS user_auth.users (
  user_id     BIGSERIAL PRIMARY KEY,
  password    VARCHAR(255) NOT NULL,
  name        VARCHAR(100) NOT NULL,
  phone       VARCHAR(11),
  email       VARCHAR(255) NOT NULL UNIQUE,
  role        user_auth.user_role NOT NULL,
  is_active   BOOLEAN NOT NULL DEFAULT TRUE,
  created_at  TIMESTAMP NOT NULL DEFAULT now(),
  updated_at  TIMESTAMP NOT NULL DEFAULT now(),
  deleted_at  TIMESTAMP,
  created_by  BIGINT,
  updated_by  BIGINT,
  deleted_by  BIGINT
);

CREATE TABLE IF NOT EXISTS user_auth.customer_addresses (
  addr_id      UUID PRIMARY KEY,
  user_id      BIGINT NOT NULL,
  addr_name    VARCHAR(50) NOT NULL,
  address1     VARCHAR(255) NOT NULL,
  address2     VARCHAR(255),
  postal_code  VARCHAR(10),
  is_default   BOOLEAN NOT NULL DEFAULT FALSE,
  created_at   TIMESTAMP NOT NULL DEFAULT now(),
  updated_at   TIMESTAMP NOT NULL DEFAULT now(),
  deleted_at   TIMESTAMP,
  created_by   BIGINT,
  updated_by   BIGINT,
  deleted_by   BIGINT
);

DO $$ BEGIN
  ALTER TABLE user_auth.customer_addresses
    ADD CONSTRAINT fk_customer_addresses_user
    FOREIGN KEY (user_id) REFERENCES user_auth.users(user_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
