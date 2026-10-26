-- Runs once when the Postgres data volume is first initialised.
-- Creates the extra databases and roles for the apps that share this instance.
CREATE ROLE bank LOGIN PASSWORD 'bank';
CREATE DATABASE external_bank OWNER bank;
