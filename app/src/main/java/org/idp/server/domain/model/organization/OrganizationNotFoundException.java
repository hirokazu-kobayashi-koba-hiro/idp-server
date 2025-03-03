package org.idp.server.domain.model.organization;

import org.idp.server.domain.model.base.NotFoundException;

public class OrganizationNotFoundException extends NotFoundException {
  public OrganizationNotFoundException(String message) {
    super(message);
  }
}
