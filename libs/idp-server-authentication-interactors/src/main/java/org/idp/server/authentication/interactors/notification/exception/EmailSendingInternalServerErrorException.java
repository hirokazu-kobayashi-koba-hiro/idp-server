package org.idp.server.authentication.interactors.notification.exception;

import org.idp.server.platform.exception.InternalServerErrorException;

public class EmailSendingInternalServerErrorException extends InternalServerErrorException {
  public EmailSendingInternalServerErrorException(String message) {
    super(message);
  }

  public EmailSendingInternalServerErrorException(String message, Throwable cause) {
    super(message, cause);
  }
}
