package org.idp.server.adapters.springboot.domain.model.authentication;

import org.idp.server.core.type.exception.NotFoundException;

public class EmailVerificationChallengeNotFoundException extends NotFoundException {
  public EmailVerificationChallengeNotFoundException(String message) {
    super(message);
  }
}
