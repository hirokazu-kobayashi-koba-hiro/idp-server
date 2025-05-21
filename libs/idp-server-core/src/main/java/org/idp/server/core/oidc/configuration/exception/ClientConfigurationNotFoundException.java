package org.idp.server.core.oidc.configuration.exception;

import org.idp.server.platform.exception.NotFoundException;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

/** ClientConfigurationNotFoundException */
public class ClientConfigurationNotFoundException extends NotFoundException {

  Tenant tenant;

  public ClientConfigurationNotFoundException(String message, Tenant tenant) {
    super(message);
    this.tenant = tenant;
  }

  public Tenant tenant() {
    return tenant;
  }
}
