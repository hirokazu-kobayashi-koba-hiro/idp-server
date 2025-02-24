package org.idp.sample.domain.model.authentication;

import org.idp.sample.domain.model.base.NotFoundException;

public class EmailVerificationChallengeNotFoundException extends NotFoundException {
  public EmailVerificationChallengeNotFoundException(String message) {
    super(message);
  }
}
