package org.idp.server.adapters.springboot.domain.model.tenant;

import org.idp.server.adapters.springboot.domain.model.base.NotFoundException;

public class TenantNotFoundException extends NotFoundException {

  public TenantNotFoundException(String message) {
    super(message);
  }
}
