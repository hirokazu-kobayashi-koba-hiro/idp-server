/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.identity.verification.application;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.schema.JsonSchemaDefinition;
import org.idp.server.core.extension.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationProcessConfiguration;

public class IdentityVerificationApplicationDetails {

  JsonNodeWrapper json;

  public IdentityVerificationApplicationDetails() {
    this.json = JsonNodeWrapper.empty();
  }

  public IdentityVerificationApplicationDetails(JsonNodeWrapper json) {
    this.json = json;
  }

  public static IdentityVerificationApplicationDetails create(
      IdentityVerificationRequest request, IdentityVerificationProcessConfiguration processConfig) {

    JsonSchemaDefinition jsonSchemaDefinition = processConfig.requestValidationSchemaAsDefinition();
    Map<String, Object> mappingResult =
        IdentityVerificationMapper.mapping(request.toMap(), jsonSchemaDefinition);

    return new IdentityVerificationApplicationDetails(JsonNodeWrapper.fromObject(mappingResult));
  }

  public IdentityVerificationApplicationDetails merge(
      IdentityVerificationRequest request, IdentityVerificationProcessConfiguration processConfig) {
    JsonSchemaDefinition jsonSchemaDefinition = processConfig.requestValidationSchemaAsDefinition();
    Map<String, Object> mappingResult =
        IdentityVerificationMapper.mapping(request.toMap(), jsonSchemaDefinition);
    Map<String, Object> mergedResult = new HashMap<>(json.toMap());
    ;
    mergedResult.putAll(mappingResult);

    return new IdentityVerificationApplicationDetails(JsonNodeWrapper.fromObject(mergedResult));
  }

  public String getValueOrEmptyAsString(String fieldName) {
    return json.getValueOrEmptyAsString(fieldName);
  }

  public Map<String, Object> toMap() {
    return json.toMap();
  }
}
