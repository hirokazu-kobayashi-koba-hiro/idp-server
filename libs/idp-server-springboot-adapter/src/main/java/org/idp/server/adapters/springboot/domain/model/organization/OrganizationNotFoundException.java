package org.idp.server.adapters.springboot.domain.model.organization;

import org.idp.server.adapters.springboot.domain.model.base.NotFoundException;

public class OrganizationNotFoundException extends NotFoundException {
  public OrganizationNotFoundException(String message) {
    super(message);
  }
}
