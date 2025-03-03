package org.idp.server.domain.model.notification.exception;

import org.idp.server.domain.model.base.TimeoutException;

public class EmailSendingTimeoutException extends TimeoutException {
  public EmailSendingTimeoutException(String message) {
    super(message);
  }
}
