package org.idp.server.adapters.springboot.domain.model.notification.exception;

import org.idp.server.adapters.springboot.domain.model.base.TimeoutException;

public class EmailSendingTimeoutException extends TimeoutException {
  public EmailSendingTimeoutException(String message) {
    super(message);
  }
}
