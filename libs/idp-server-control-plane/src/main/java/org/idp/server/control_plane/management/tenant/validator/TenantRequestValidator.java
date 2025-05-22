/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.management.tenant.validator;

import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.basic.json.schema.JsonSchemaValidator;
import org.idp.server.control_plane.base.schema.SchemaReader;
import org.idp.server.control_plane.management.onboarding.io.OnboardingRequest;

public class TenantRequestValidator {

  OnboardingRequest request;
  JsonSchemaValidator tenantSchemaValidator;
  JsonSchemaValidator authorizationServerSchemaValidator;
  boolean dryRun;

  public TenantRequestValidator(OnboardingRequest request, boolean dryRun) {
    this.request = request;
    this.tenantSchemaValidator = new JsonSchemaValidator(SchemaReader.tenantSchema());
    this.authorizationServerSchemaValidator =
        new JsonSchemaValidator(SchemaReader.authorizationServerSchema());
    this.dryRun = dryRun;
  }

  public TenantRequestValidationResult validate() {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(request.toMap());
    JsonSchemaValidationResult tenantResult =
        tenantSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("tenant"));
    JsonSchemaValidationResult authorizationServerResult =
        authorizationServerSchemaValidator.validate(
            jsonNodeWrapper.getValueAsJsonNode("authorization_server"));

    if (!tenantResult.isValid() || !authorizationServerResult.isValid()) {
      return TenantRequestValidationResult.error(tenantResult, authorizationServerResult, dryRun);
    }

    return TenantRequestValidationResult.success(tenantResult, authorizationServerResult, dryRun);
  }
}
