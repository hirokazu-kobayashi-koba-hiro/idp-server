package org.idp.server.domain.model.tenant;

import org.idp.server.domain.model.base.NotFoundException;

public class TenantNotFoundException extends NotFoundException {

  public TenantNotFoundException(String message) {
    super(message);
  }
}
