/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.identity.verification.delegation;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.schema.JsonSchemaDefinition;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationMapper;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationProcessConfiguration;

public class ExternalWorkflowApplicationDetails {

  JsonNodeWrapper json;

  public ExternalWorkflowApplicationDetails() {
    this.json = JsonNodeWrapper.empty();
  }

  public ExternalWorkflowApplicationDetails(JsonNodeWrapper json) {
    this.json = json;
  }

  public static ExternalWorkflowApplicationDetails create(
      JsonNodeWrapper body, IdentityVerificationProcessConfiguration processConfig) {
    JsonSchemaDefinition jsonSchemaDefinition =
        processConfig.responseValidationSchemaAsDefinition();
    Map<String, Object> mappingResult =
        IdentityVerificationMapper.mapping(body.toMap(), jsonSchemaDefinition);
    return new ExternalWorkflowApplicationDetails(JsonNodeWrapper.fromObject(mappingResult));
  }

  public ExternalWorkflowApplicationDetails merge(
      JsonNodeWrapper body, IdentityVerificationProcessConfiguration processConfig) {
    JsonSchemaDefinition jsonSchemaDefinition =
        processConfig.responseValidationSchemaAsDefinition();
    Map<String, Object> mappingResult =
        IdentityVerificationMapper.mapping(body.toMap(), jsonSchemaDefinition);
    Map<String, Object> merged = new HashMap<>(json.toMap());
    merged.putAll(mappingResult);
    return new ExternalWorkflowApplicationDetails(JsonNodeWrapper.fromObject(merged));
  }

  public String getValueOrEmptyAsString(String fieldName) {
    return json.getValueOrEmptyAsString(fieldName);
  }

  public Map<String, Object> toMap() {
    return json.toMap();
  }

  public boolean exists() {
    return json != null && json.exists();
  }
}
