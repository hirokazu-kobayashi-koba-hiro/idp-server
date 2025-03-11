package org.idp.server.adapters.springboot.domain.model.notification.exception;

import org.idp.server.adapters.springboot.domain.model.base.InternalServerErrorException;

public class EmailSendingInternalServerErrorException extends InternalServerErrorException {
  public EmailSendingInternalServerErrorException(String message) {
    super(message);
  }

  public EmailSendingInternalServerErrorException(String message, Throwable cause) {
    super(message, cause);
  }
}
