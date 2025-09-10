-- Add log-specific columns to security_event table for enhanced logging capabilities
-- This supports stage tracking and automatic event categorization

ALTER TABLE security_event 
ADD COLUMN stage VARCHAR(20),
ADD COLUMN tags JSONB;

-- Add indexes for new columns
CREATE INDEX idx_security_event_stage ON security_event (tenant_id, stage);
CREATE INDEX idx_security_event_tags_gin ON security_event USING GIN (tags);

-- Add comments
COMMENT ON COLUMN security_event.stage IS 'Processing stage: received, processed, etc.';
COMMENT ON COLUMN security_event.tags IS 'Automatic categorization tags: ["authentication", "success"], etc.';