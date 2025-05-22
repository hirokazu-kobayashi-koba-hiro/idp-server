/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.management.federation;

import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.control_plane.management.federation.io.FederationConfigRequest;
import org.idp.server.core.oidc.federation.FederationConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class FederationConfigUpdateContextCreator {

  Tenant tenant;
  FederationConfiguration before;
  FederationConfigRequest request;
  boolean dryRun;
  JsonConverter jsonConverter;

  public FederationConfigUpdateContextCreator(
      Tenant tenant,
      FederationConfiguration before,
      FederationConfigRequest request,
      boolean dryRun) {
    this.tenant = tenant;
    this.before = before;
    this.request = request;
    this.dryRun = dryRun;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public FederationConfigUpdateContext create() {
    JsonNodeWrapper configJson = jsonConverter.readTree(request.toMap());
    String id = configJson.getValueOrEmptyAsString("id");
    String type = configJson.getValueOrEmptyAsString("type");
    String ssoProvider = configJson.getValueOrEmptyAsString("sso_provider");
    JsonNodeWrapper payloadJson = configJson.getValueAsJsonNode("payload");
    Map<String, Object> payload = payloadJson.toMap();

    FederationConfiguration after = new FederationConfiguration(id, type, ssoProvider, payload);

    return new FederationConfigUpdateContext(tenant, before, after, dryRun);
  }
}
