package org.idp.server.core.authentication.notification.exception;

import org.idp.server.platform.exception.TimeoutException;

public class EmailSendingTimeoutException extends TimeoutException {
  public EmailSendingTimeoutException(String message) {
    super(message);
  }
}
