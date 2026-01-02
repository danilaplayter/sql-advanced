-- src/main/resources/db/migrations/001-create-schema.sql
--liquibase formatted sql
--changeset mp161:create-schema
CREATE SCHEMA IF NOT EXISTS mentee_power;
SET search_path TO mentee_power;
--rollback DROP SCHEMA IF EXISTS mentee_power CASCADE;