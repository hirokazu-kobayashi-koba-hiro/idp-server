package org.idp.sample.domain.model.authentication;

import org.idp.sample.domain.model.base.BadRequestException;

public class EmailVerificationCodeUnmatchException extends BadRequestException {
  public EmailVerificationCodeUnmatchException(String message) {
    super(message);
  }
}
