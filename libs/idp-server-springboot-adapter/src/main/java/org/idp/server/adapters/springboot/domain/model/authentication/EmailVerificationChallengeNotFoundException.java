package org.idp.server.adapters.springboot.domain.model.authentication;

import org.idp.server.adapters.springboot.domain.model.base.NotFoundException;

public class EmailVerificationChallengeNotFoundException extends NotFoundException {
  public EmailVerificationChallengeNotFoundException(String message) {
    super(message);
  }
}
