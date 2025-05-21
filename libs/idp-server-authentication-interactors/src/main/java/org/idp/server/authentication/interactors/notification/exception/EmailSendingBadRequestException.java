package org.idp.server.authentication.interactors.notification.exception;

import org.idp.server.platform.exception.BadRequestException;

public class EmailSendingBadRequestException extends BadRequestException {
  public EmailSendingBadRequestException(String message) {
    super(message);
  }
}
