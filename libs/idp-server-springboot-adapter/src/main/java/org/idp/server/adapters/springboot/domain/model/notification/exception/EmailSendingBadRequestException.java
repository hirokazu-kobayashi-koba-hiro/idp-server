package org.idp.server.adapters.springboot.domain.model.notification.exception;

import org.idp.server.core.type.exception.BadRequestException;

public class EmailSendingBadRequestException extends BadRequestException {
  public EmailSendingBadRequestException(String message) {
    super(message);
  }
}
