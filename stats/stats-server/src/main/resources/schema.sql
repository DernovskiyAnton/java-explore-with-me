CREATE TABLE IF NOT EXISTS endpoint_hits (
    id BIGSERIAL PRIMARY KEY,
    app VARCHAR(255) NOT NULL,
    uri VARCHAR(512) NOT NULL,
    ip VARCHAR(45) NOT NULL,
    timestamp TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_timestamp ON endpoint_hits(timestamp);
CREATE INDEX IF NOT EXISTS idx_uri ON endpoint_hits(uri);
CREATE INDEX IF NOT EXISTS idx_app ON endpoint_hits(app);