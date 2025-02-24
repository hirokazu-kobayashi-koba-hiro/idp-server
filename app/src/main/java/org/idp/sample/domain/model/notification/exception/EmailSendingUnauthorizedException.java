package org.idp.sample.domain.model.notification.exception;

import org.idp.sample.domain.model.base.UnauthorizedException;

public class EmailSendingUnauthorizedException extends UnauthorizedException {
  public EmailSendingUnauthorizedException(String message) {
    super(message);
  }
}
