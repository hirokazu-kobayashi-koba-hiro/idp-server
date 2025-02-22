package org.idp.sample.domain.model.organization.initial;

import org.idp.sample.domain.model.base.ForbiddenException;

public class InitialRegistrationForbiddenException extends ForbiddenException {
  public InitialRegistrationForbiddenException(String message) {
    super(message);
  }
}
