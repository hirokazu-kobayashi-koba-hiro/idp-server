package org.idp.server.domain.model.organization.initial;

import org.idp.server.domain.model.base.ForbiddenException;

public class InitialRegistrationForbiddenException extends ForbiddenException {
  public InitialRegistrationForbiddenException(String message) {
    super(message);
  }
}
