package org.idp.server.core.authentication.email;

import org.idp.server.platform.exception.NotFoundException;

public class EmailVerificationChallengeNotFoundException extends NotFoundException {
  public EmailVerificationChallengeNotFoundException(String message) {
    super(message);
  }
}
