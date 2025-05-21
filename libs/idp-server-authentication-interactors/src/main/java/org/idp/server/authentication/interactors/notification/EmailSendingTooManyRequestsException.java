package org.idp.server.authentication.interactors.notification;

import org.idp.server.platform.exception.TooManyRequestsException;

public class EmailSendingTooManyRequestsException extends TooManyRequestsException {
  public EmailSendingTooManyRequestsException(String message) {
    super(message);
  }

  public EmailSendingTooManyRequestsException(String message, Throwable cause) {
    super(message, cause);
  }
}
