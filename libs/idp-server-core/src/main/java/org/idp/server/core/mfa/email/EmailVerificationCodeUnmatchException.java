package org.idp.server.core.mfa.email;

import org.idp.server.core.type.exception.BadRequestException;

public class EmailVerificationCodeUnmatchException extends BadRequestException {
  public EmailVerificationCodeUnmatchException(String message) {
    super(message);
  }
}
