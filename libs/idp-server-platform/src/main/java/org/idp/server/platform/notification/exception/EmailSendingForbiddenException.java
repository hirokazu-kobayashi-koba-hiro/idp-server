package org.idp.server.platform.notification.exception;

import org.idp.server.platform.exception.ForbiddenException;

public class EmailSendingForbiddenException extends ForbiddenException {
  public EmailSendingForbiddenException(String message) {
    super(message);
  }
}
