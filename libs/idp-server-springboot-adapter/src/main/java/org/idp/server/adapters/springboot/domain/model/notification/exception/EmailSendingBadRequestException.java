package org.idp.server.adapters.springboot.domain.model.notification.exception;

import org.idp.server.adapters.springboot.domain.model.base.BadRequestException;

public class EmailSendingBadRequestException extends BadRequestException {
  public EmailSendingBadRequestException(String message) {
    super(message);
  }
}
