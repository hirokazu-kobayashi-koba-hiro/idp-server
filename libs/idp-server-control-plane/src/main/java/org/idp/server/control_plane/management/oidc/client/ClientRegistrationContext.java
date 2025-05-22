/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.management.oidc.client;

import java.util.Map;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementResponse;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementStatus;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class ClientRegistrationContext {
  Tenant tenant;
  ClientConfiguration clientConfiguration;
  boolean dryRun;

  public ClientRegistrationContext(
      Tenant tenant, ClientConfiguration clientConfiguration, boolean dryRun) {
    this.tenant = tenant;
    this.clientConfiguration = clientConfiguration;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return tenant;
  }

  public ClientConfiguration configuration() {
    return clientConfiguration;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public ClientManagementResponse toResponse() {
    Map<String, Object> contents = Map.of("result", clientConfiguration.toMap(), "dry_run", dryRun);
    return new ClientManagementResponse(ClientManagementStatus.CREATED, contents);
  }
}
