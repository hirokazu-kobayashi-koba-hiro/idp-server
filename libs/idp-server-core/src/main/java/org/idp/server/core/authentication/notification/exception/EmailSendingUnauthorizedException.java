package org.idp.server.core.authentication.notification.exception;

import org.idp.server.platform.exception.UnauthorizedException;

public class EmailSendingUnauthorizedException extends UnauthorizedException {
  public EmailSendingUnauthorizedException(String message) {
    super(message);
  }
}
