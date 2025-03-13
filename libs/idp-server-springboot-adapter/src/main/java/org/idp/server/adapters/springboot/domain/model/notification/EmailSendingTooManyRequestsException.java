package org.idp.server.adapters.springboot.domain.model.notification;

import org.idp.server.core.type.exception.TooManyRequestsException;

public class EmailSendingTooManyRequestsException extends TooManyRequestsException {
  public EmailSendingTooManyRequestsException(String message) {
    super(message);
  }

  public EmailSendingTooManyRequestsException(String message, Throwable cause) {
    super(message, cause);
  }
}
