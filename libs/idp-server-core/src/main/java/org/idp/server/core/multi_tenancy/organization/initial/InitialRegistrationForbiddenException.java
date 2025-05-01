package org.idp.server.core.multi_tenancy.organization.initial;

import org.idp.server.basic.exception.ForbiddenException;

public class InitialRegistrationForbiddenException extends ForbiddenException {
  public InitialRegistrationForbiddenException(String message) {
    super(message);
  }
}
