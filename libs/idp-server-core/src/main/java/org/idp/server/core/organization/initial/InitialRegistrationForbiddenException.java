package org.idp.server.core.organization.initial;

import org.idp.server.core.type.exception.ForbiddenException;

public class InitialRegistrationForbiddenException extends ForbiddenException {
  public InitialRegistrationForbiddenException(String message) {
    super(message);
  }
}
