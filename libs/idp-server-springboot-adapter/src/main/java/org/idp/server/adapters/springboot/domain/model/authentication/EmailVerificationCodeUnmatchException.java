package org.idp.server.adapters.springboot.domain.model.authentication;

import org.idp.server.adapters.springboot.domain.model.base.BadRequestException;

public class EmailVerificationCodeUnmatchException extends BadRequestException {
  public EmailVerificationCodeUnmatchException(String message) {
    super(message);
  }
}
