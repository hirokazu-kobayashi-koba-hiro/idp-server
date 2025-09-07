-- Add enabled column to client_configuration table
ALTER TABLE client_configuration ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- Add enabled column to authorization_server_configuration table
ALTER TABLE authorization_server_configuration ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- Add enabled column to federation_configurations table
ALTER TABLE federation_configurations ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- Create indexes for enabled columns for better query performance
CREATE INDEX idx_client_configuration_enabled ON client_configuration (tenant_id, enabled);
CREATE INDEX idx_authorization_server_configuration_enabled ON authorization_server_configuration (tenant_id, enabled);
CREATE INDEX idx_federation_configurations_enabled ON federation_configurations (tenant_id, enabled);