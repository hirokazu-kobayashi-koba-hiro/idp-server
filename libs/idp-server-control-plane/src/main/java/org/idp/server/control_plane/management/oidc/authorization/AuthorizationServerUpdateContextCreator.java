/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.management.oidc.authorization;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerUpdateRequest;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthorizationServerUpdateContextCreator {

  Tenant tenant;
  AuthorizationServerConfiguration before;
  AuthorizationServerUpdateRequest request;
  boolean dryRun;
  JsonConverter jsonConverter;

  public AuthorizationServerUpdateContextCreator(
      Tenant tenant,
      AuthorizationServerConfiguration before,
      AuthorizationServerUpdateRequest request,
      boolean dryRun) {
    this.tenant = tenant;
    this.before = before;
    this.request = request;
    this.dryRun = dryRun;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public AuthorizationServerUpdateContext create() {
    AuthorizationServerConfiguration configuration =
        jsonConverter.read(request.toMap(), AuthorizationServerConfiguration.class);

    return new AuthorizationServerUpdateContext(tenant, before, configuration, dryRun);
  }
}
