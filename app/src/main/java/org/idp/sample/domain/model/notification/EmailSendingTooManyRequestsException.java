package org.idp.sample.domain.model.notification;

import org.idp.sample.domain.model.base.TooManyRequestsException;

public class EmailSendingTooManyRequestsException extends TooManyRequestsException {
  public EmailSendingTooManyRequestsException(String message) {
    super(message);
  }

  public EmailSendingTooManyRequestsException(String message, Throwable cause) {
    super(message, cause);
  }
}
