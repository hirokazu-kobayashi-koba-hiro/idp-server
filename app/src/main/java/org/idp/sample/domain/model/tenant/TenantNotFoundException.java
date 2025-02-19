package org.idp.sample.domain.model.tenant;

import org.idp.sample.domain.model.base.NotFoundException;

public class TenantNotFoundException extends NotFoundException {

  public TenantNotFoundException(String message) {
    super(message);
  }
}
