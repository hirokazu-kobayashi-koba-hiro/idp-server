/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.identity.verification.application;

import java.util.*;
import org.idp.server.basic.json.schema.JsonSchemaDefinition;
import org.idp.server.basic.json.schema.JsonSchemaProperty;

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
