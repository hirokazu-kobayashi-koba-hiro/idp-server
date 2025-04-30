package org.idp.server.core.identity.verification.application;

import java.util.*;
import org.idp.server.core.basic.json.schema.JsonSchemaDefinition;
import org.idp.server.core.basic.json.schema.JsonSchemaProperty;

public class IdentityVerificationMapper {

  public static Map<String, Object> mapping(
      Map<String, Object> request, JsonSchemaDefinition schemaDefinition) {
    Map<String, Object> mappingResult = new HashMap<>();
    Map<String, JsonSchemaProperty> properties = schemaDefinition.getProperties();

    for (Map.Entry<String, JsonSchemaProperty> propertySchema : properties.entrySet()) {
      String propertyName = propertySchema.getKey();
      JsonSchemaProperty property = propertySchema.getValue();
      if (property.shouldStore()) {
        Object value = request.get(propertyName);
        if (value != null) {
          mappingResult.put(propertyName, value);
        }
      }
    }

    return mappingResult;
  }
}
