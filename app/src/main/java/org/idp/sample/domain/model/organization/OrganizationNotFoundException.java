package org.idp.sample.domain.model.organization;

import org.idp.sample.domain.model.base.NotFoundException;

public class OrganizationNotFoundException extends NotFoundException {
  public OrganizationNotFoundException(String message) {
    super(message);
  }
}
