package org.idp.server.control_plane.management.oidc.client;

import java.util.Map;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementResponse;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementStatus;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;

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
