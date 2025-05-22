/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.identity.verification.claims;

import java.util.Map;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.schema.JsonSchemaDefinition;
import org.idp.server.core.extension.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationMapper;

public class VerifiedClaims {
  JsonNodeWrapper json;

  public VerifiedClaims() {
    this.json = JsonNodeWrapper.empty();
  }

  public VerifiedClaims(JsonNodeWrapper json) {
    this.json = json;
  }

  public static VerifiedClaims create(
      IdentityVerificationRequest request, JsonSchemaDefinition jsonSchemaDefinition) {

    Map<String, Object> mappingResult =
        IdentityVerificationMapper.mapping(request.toMap(), jsonSchemaDefinition);

    return new VerifiedClaims(JsonNodeWrapper.fromObject(mappingResult));
  }

  public String getValueOrEmptyAsString(String fieldName) {
    return json.getValueOrEmptyAsString(fieldName);
  }

  public Map<String, Object> toMap() {
    return json.toMap();
  }
}
