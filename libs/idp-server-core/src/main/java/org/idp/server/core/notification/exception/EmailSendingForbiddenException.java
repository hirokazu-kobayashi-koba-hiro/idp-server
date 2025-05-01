package org.idp.server.core.notification.exception;

import org.idp.server.basic.exception.ForbiddenException;

public class EmailSendingForbiddenException extends ForbiddenException {
  public EmailSendingForbiddenException(String message) {
    super(message);
  }
}
