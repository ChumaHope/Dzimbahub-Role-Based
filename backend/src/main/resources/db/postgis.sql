-- Run this on a PostgreSQL database where PostGIS is enabled.
CREATE EXTENSION IF NOT EXISTS postgis;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS location geography(Point,4326);
UPDATE properties SET location = ST_SetSRID(ST_MakePoint(longitude, latitude),4326)::geography WHERE location IS NULL;
CREATE INDEX IF NOT EXISTS idx_properties_location ON properties USING GIST(location);
