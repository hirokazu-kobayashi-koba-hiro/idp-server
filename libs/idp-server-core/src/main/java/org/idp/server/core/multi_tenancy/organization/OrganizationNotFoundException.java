package org.idp.server.core.multi_tenancy.organization;

import org.idp.server.basic.exception.NotFoundException;

public class OrganizationNotFoundException extends NotFoundException {
  public OrganizationNotFoundException(String message) {
    super(message);
  }
}
