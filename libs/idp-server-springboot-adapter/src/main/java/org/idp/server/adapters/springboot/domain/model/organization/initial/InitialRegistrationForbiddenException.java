package org.idp.server.adapters.springboot.domain.model.organization.initial;

import org.idp.server.adapters.springboot.domain.model.base.ForbiddenException;

public class InitialRegistrationForbiddenException extends ForbiddenException {
  public InitialRegistrationForbiddenException(String message) {
    super(message);
  }
}
