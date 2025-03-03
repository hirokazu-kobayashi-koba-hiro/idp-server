package org.idp.server.domain.model.notification;

import org.idp.server.domain.model.base.TooManyRequestsException;

public class EmailSendingTooManyRequestsException extends TooManyRequestsException {
  public EmailSendingTooManyRequestsException(String message) {
    super(message);
  }

  public EmailSendingTooManyRequestsException(String message, Throwable cause) {
    super(message, cause);
  }
}
