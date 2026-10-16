-- Optional manual alternative to the server's automatic schema creation.
-- Run from project root: mysql -u root -p < sql/create-and-populate.sql
CREATE DATABASE IF NOT EXISTS drs_enhanced CHARACTER SET utf8mb4;
USE drs_enhanced;
SOURCE sql/schema.sql;
-- Demonstration reference data, safe to re-run. Incident data is imported through
-- the application server so encryption and audit records are generated correctly.
INSERT INTO shelters(name,location,capacity,occupied)
SELECT 'Central Community Hall','Brisbane - demonstration site',100,0
WHERE NOT EXISTS(SELECT 1 FROM shelters WHERE name='Central Community Hall');
INSERT INTO shelters(name,location,capacity,occupied)
SELECT 'School Evacuation Centre','Sydney - demonstration site',150,0
WHERE NOT EXISTS(SELECT 1 FROM shelters WHERE name='School Evacuation Centre');
-- Admin credentials are generated locally by setup and hashed by the server.
-- Do not put plaintext passwords or unencrypted incident descriptions in SQL.
