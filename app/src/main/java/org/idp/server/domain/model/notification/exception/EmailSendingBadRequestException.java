package org.idp.server.domain.model.notification.exception;

import org.idp.server.domain.model.base.BadRequestException;

public class EmailSendingBadRequestException extends BadRequestException {
  public EmailSendingBadRequestException(String message) {
    super(message);
  }
}
