package org.idp.server.domain.model.authentication;

import org.idp.server.domain.model.base.BadRequestException;

public class EmailVerificationCodeUnmatchException extends BadRequestException {
  public EmailVerificationCodeUnmatchException(String message) {
    super(message);
  }
}
