package org.idp.server.platform.notification.exception;

import org.idp.server.platform.exception.UnauthorizedException;

public class EmailSendingUnauthorizedException extends UnauthorizedException {
  public EmailSendingUnauthorizedException(String message) {
    super(message);
  }
}
