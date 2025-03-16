package org.idp.server.core.notification.exception;

import org.idp.server.core.type.exception.TimeoutException;

public class EmailSendingTimeoutException extends TimeoutException {
  public EmailSendingTimeoutException(String message) {
    super(message);
  }
}
