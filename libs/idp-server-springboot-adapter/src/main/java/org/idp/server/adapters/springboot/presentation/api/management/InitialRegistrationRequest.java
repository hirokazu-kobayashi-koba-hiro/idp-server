package org.idp.server.adapters.springboot.presentation.api.management;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.idp.server.adapters.springboot.domain.model.organization.OrganizationName;
import org.idp.server.adapters.springboot.domain.model.tenant.TenantName;

// TODO set annotation for validation
public class InitialRegistrationRequest {

  @JsonProperty("organization_name")
  String organizationName;

  @JsonProperty("tenant_name")
  String tenantName;

  @JsonProperty("server_config")
  String serverConfig;

  public OrganizationName organizationName() {
    return new OrganizationName(organizationName);
  }

  public TenantName tenantName() {
    return new TenantName(tenantName);
  }

  public String serverConfig() {
    return serverConfig;
  }
}
