-- Add execution payload column to security_event_hook_results table
-- This allows storing the execution result data for resending capabilities
-- Issue: #288

ALTER TABLE security_event_hook_results 
ADD COLUMN security_event_hook_execution_payload JSON;

-- Add comment to explain the purpose of the new column
ALTER TABLE security_event_hook_results 
MODIFY COLUMN security_event_hook_execution_payload JSON COMMENT 
'Stores the execution result payload from security event hooks for resending and debugging purposes';