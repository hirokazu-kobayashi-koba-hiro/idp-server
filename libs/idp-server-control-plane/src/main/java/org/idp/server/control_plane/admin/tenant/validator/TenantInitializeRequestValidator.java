/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.admin.tenant.validator;

import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.basic.json.schema.JsonSchemaValidator;
import org.idp.server.control_plane.admin.tenant.io.TenantInitializationRequest;
import org.idp.server.control_plane.base.schema.SchemaReader;

public class TenantInitializeRequestValidator {

  TenantInitializationRequest request;
  JsonSchemaValidator organizationSchemaValidator;
  JsonSchemaValidator tenantSchemaValidator;
  JsonSchemaValidator authorizationServerSchemaValidator;
  JsonSchemaValidator adminUserSchemaValidator;
  JsonSchemaValidator clientSchemaValidator;
  boolean dryRun;

  public TenantInitializeRequestValidator(TenantInitializationRequest request, boolean dryRun) {
    this.request = request;
    this.organizationSchemaValidator = new JsonSchemaValidator(SchemaReader.organizationSchema());
    this.tenantSchemaValidator = new JsonSchemaValidator(SchemaReader.tenantSchema());
    this.authorizationServerSchemaValidator =
        new JsonSchemaValidator(SchemaReader.authorizationServerSchema());
    this.adminUserSchemaValidator = new JsonSchemaValidator(SchemaReader.adminUserSchema());
    this.clientSchemaValidator = new JsonSchemaValidator(SchemaReader.clientSchema());
    this.dryRun = dryRun;
  }

  public TenantInitializeRequestValidationResult validate() {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(request.toMap());
    JsonSchemaValidationResult organizationResult =
        organizationSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("organization"));
    JsonSchemaValidationResult tenantResult =
        tenantSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("tenant"));
    JsonSchemaValidationResult authorizationServerResult =
        authorizationServerSchemaValidator.validate(
            jsonNodeWrapper.getValueAsJsonNode("authorization_server"));
    JsonSchemaValidationResult adminUserResult =
        adminUserSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("user"));
    JsonSchemaValidationResult clientResult =
        clientSchemaValidator.validate(jsonNodeWrapper.getValueAsJsonNode("client"));

    if (!organizationResult.isValid()
        || !tenantResult.isValid()
        || !authorizationServerResult.isValid()
        || !adminUserResult.isValid()) {
      return TenantInitializeRequestValidationResult.error(
          organizationResult,
          tenantResult,
          authorizationServerResult,
          adminUserResult,
          clientResult,
          dryRun);
    }

    return TenantInitializeRequestValidationResult.success(
        organizationResult,
        tenantResult,
        authorizationServerResult,
        adminUserResult,
        clientResult,
        dryRun);
  }
}
