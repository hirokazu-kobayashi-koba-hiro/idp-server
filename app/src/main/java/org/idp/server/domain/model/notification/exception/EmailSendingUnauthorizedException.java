package org.idp.server.domain.model.notification.exception;

import org.idp.server.domain.model.base.UnauthorizedException;

public class EmailSendingUnauthorizedException extends UnauthorizedException {
  public EmailSendingUnauthorizedException(String message) {
    super(message);
  }
}
