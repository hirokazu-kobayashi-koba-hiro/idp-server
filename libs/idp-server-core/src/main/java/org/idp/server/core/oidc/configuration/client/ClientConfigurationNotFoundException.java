package org.idp.server.core.oidc.configuration.client;

import org.idp.server.core.multi_tenancy.tenant.Tenant;

/** ClientConfigurationNotFoundException */
public class ClientConfigurationNotFoundException extends RuntimeException {

  Tenant tenant;

  public ClientConfigurationNotFoundException(String message, Tenant tenant) {
    super(message);
    this.tenant = tenant;
  }

  public Tenant tenant() {
    return tenant;
  }
}
