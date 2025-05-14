package org.idp.server.control_plane.management.federation;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementResponse;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementStatus;
import org.idp.server.core.federation.FederationConfiguration;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class FederationConfigRegistrationContext {

  Tenant tenant;
  FederationConfiguration federationConfiguration;
  boolean dryRun;

  public FederationConfigRegistrationContext(
      Tenant tenant, FederationConfiguration federationConfiguration, boolean dryRun) {
    this.tenant = tenant;
    this.federationConfiguration = federationConfiguration;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return tenant;
  }

  public FederationConfiguration federationConfiguration() {
    return federationConfiguration;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public FederationConfigManagementResponse toResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("config", federationConfiguration.payload());
    response.put("dry_run", dryRun);
    return new FederationConfigManagementResponse(
        FederationConfigManagementStatus.CREATED, response);
  }
}
