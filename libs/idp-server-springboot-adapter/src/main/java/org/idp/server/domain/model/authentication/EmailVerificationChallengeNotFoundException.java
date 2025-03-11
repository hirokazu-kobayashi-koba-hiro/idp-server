package org.idp.server.domain.model.authentication;

import org.idp.server.domain.model.base.NotFoundException;

public class EmailVerificationChallengeNotFoundException extends NotFoundException {
  public EmailVerificationChallengeNotFoundException(String message) {
    super(message);
  }
}
