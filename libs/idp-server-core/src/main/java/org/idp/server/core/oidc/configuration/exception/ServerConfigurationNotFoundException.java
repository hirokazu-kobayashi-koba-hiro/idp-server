package org.idp.server.core.oidc.configuration.exception;

import org.idp.server.basic.exception.NotFoundException;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

/** ServerConfigurationNotFoundException */
public class ServerConfigurationNotFoundException extends NotFoundException {

  Tenant tenant;

  public ServerConfigurationNotFoundException(String message, Tenant tenant) {
    super(message);
    this.tenant = tenant;
  }

  public Tenant tenant() {
    return tenant;
  }
}
