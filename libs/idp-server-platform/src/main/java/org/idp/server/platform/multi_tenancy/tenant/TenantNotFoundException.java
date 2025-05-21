package org.idp.server.platform.multi_tenancy.tenant;

import org.idp.server.platform.exception.NotFoundException;

public class TenantNotFoundException extends NotFoundException {

  public TenantNotFoundException(String message) {
    super(message);
  }
}
