package org.idp.sample.domain.model.organization.initial;

import org.idp.sample.domain.model.tenant.Tenant;

public class ServerConfigurationCreator {
  Tenant tenant;
  String serverConfigurationJson;

  public ServerConfigurationCreator(Tenant tenant, String serverConfigurationJson) {
    this.tenant = tenant;
    this.serverConfigurationJson = serverConfigurationJson;
  }

  public String create() {
    String issuer = tenant.issuer();
    return serverConfigurationJson.replaceAll("IDP_ISSUER", issuer);
  }
}
