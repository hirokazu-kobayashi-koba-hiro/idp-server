package org.idp.server.core.authentication.notification.exception;

import org.idp.server.basic.exception.InternalServerErrorException;

public class EmailSendingInternalServerErrorException extends InternalServerErrorException {
  public EmailSendingInternalServerErrorException(String message) {
    super(message);
  }

  public EmailSendingInternalServerErrorException(String message, Throwable cause) {
    super(message, cause);
  }
}
