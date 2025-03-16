package org.idp.server.core.notification.exception;

import org.idp.server.core.type.exception.UnauthorizedException;

public class EmailSendingUnauthorizedException extends UnauthorizedException {
  public EmailSendingUnauthorizedException(String message) {
    super(message);
  }
}
