package org.idp.server.core.tenant;

import org.idp.server.basic.exception.NotFoundException;

public class TenantNotFoundException extends NotFoundException {

  public TenantNotFoundException(String message) {
    super(message);
  }
}
