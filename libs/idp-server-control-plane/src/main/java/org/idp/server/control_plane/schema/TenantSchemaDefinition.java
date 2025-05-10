package org.idp.server.control_plane.schema;

import org.idp.server.basic.json.schema.JsonSchemaDefinition;

public class TenantSchemaDefinition {

  JsonSchemaDefinition definition;

  public TenantSchemaDefinition() {
    String json =
        """
                {
                  "type": "object",
                  "required": ["id", "name", "authorization_provider", "database_type"],
                  "properties": {
                    "id": {
                      "type": "string",
                      "minLength": 36,
                      "maxLength": 36
                    },
                    "name": {
                      "type": "string",
                      "minLength": 1
                    },
                    "database_type": {
                      "type": "string",
                      "enum": ["POSTGRESQL", "MYSQL", "SPANNER"]
                    },
                    "authorization_provider": {
                    "type": "string",
                    "enum": ["idp-server"]
                    }
                  }
                }
                """;
    this.definition = JsonSchemaDefinition.fromJson(json);
  }

  public JsonSchemaDefinition definition() {
    return definition;
  }
}
