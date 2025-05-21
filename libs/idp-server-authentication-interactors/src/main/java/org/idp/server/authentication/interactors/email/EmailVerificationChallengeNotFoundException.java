package org.idp.server.authentication.interactors.email;

import org.idp.server.platform.exception.NotFoundException;

public class EmailVerificationChallengeNotFoundException extends NotFoundException {
  public EmailVerificationChallengeNotFoundException(String message) {
    super(message);
  }
}
