-- Add log-specific columns to security_event table for enhanced logging capabilities
-- This supports stage tracking and automatic event categorization

ALTER TABLE security_event 
ADD COLUMN stage VARCHAR(20),
ADD COLUMN tags JSON;

-- Add indexes for new columns
CREATE INDEX idx_security_event_stage ON security_event (tenant_id, stage);

-- Add comments (MySQL 8.0+ supports column comments)
ALTER TABLE security_event 
MODIFY COLUMN stage VARCHAR(20) COMMENT 'Processing stage: received, processed, etc.',
MODIFY COLUMN tags JSON COMMENT 'Automatic categorization tags: ["authentication", "success"], etc.';