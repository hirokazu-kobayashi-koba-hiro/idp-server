package org.idp.server.core.organization;

import org.idp.server.core.type.exception.NotFoundException;

public class OrganizationNotFoundException extends NotFoundException {
  public OrganizationNotFoundException(String message) {
    super(message);
  }
}
