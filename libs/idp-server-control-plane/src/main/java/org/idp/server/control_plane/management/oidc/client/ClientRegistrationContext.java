package org.idp.server.control_plane.management.oidc.client;

import java.util.Map;
import org.idp.server.control_plane.management.oidc.client.io.ClientConfigurationManagementResponse;
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

  public ClientConfiguration clientConfiguration() {
    return clientConfiguration;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public ClientConfigurationManagementResponse toResponse() {
    Map<String, Object> contents = Map.of("client", clientConfiguration.toMap(), "dry_run", dryRun);
    return new ClientConfigurationManagementResponse(ClientManagementStatus.CREATED, contents);
  }
}
