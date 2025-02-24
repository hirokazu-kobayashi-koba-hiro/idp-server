package org.idp.sample.domain.model.notification.exception;

import org.idp.sample.domain.model.base.TimeoutException;

public class EmailSendingTimeoutException extends TimeoutException {
  public EmailSendingTimeoutException(String message) {
    super(message);
  }
}
