package org.idp.server.core.notification.exception;

import org.idp.server.core.type.exception.InternalServerErrorException;

public class EmailSendingInternalServerErrorException extends InternalServerErrorException {
  public EmailSendingInternalServerErrorException(String message) {
    super(message);
  }

  public EmailSendingInternalServerErrorException(String message, Throwable cause) {
    super(message, cause);
  }
}
