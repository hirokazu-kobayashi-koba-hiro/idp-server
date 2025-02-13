package org.idp.sample.presentation.api.management;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.idp.sample.domain.model.organization.OrganizationName;
import org.idp.sample.domain.model.tenant.TenantName;

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
