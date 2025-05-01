package org.idp.server.core.authentication.email;

import org.idp.server.basic.exception.BadRequestException;

public class EmailVerificationCodeUnmatchException extends BadRequestException {
  public EmailVerificationCodeUnmatchException(String message) {
    super(message);
  }
}
