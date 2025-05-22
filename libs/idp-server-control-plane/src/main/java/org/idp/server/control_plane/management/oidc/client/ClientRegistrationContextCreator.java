/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.management.oidc.client;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.control_plane.management.oidc.client.io.ClientRegistrationRequest;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class ClientRegistrationContextCreator {

  Tenant tenant;
  ClientRegistrationRequest request;
  boolean dryRun;
  JsonConverter jsonConverter;

  public ClientRegistrationContextCreator(
      Tenant tenant, ClientRegistrationRequest request, boolean dryRun) {
    this.tenant = tenant;
    this.request = request;
    this.dryRun = dryRun;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public ClientRegistrationContext create() {
    ClientConfiguration clientConfiguration =
        jsonConverter.read(request.toMap(), ClientConfiguration.class);

    return new ClientRegistrationContext(tenant, clientConfiguration, dryRun);
  }
}
