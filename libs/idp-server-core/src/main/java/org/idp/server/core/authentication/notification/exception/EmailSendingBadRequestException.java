package org.idp.server.core.authentication.notification.exception;

import org.idp.server.basic.exception.BadRequestException;

public class EmailSendingBadRequestException extends BadRequestException {
  public EmailSendingBadRequestException(String message) {
    super(message);
  }
}
