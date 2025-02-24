package org.idp.sample.domain.model.notification.exception;

import org.idp.sample.domain.model.base.BadRequestException;

public class EmailSendingBadRequestException extends BadRequestException {
  public EmailSendingBadRequestException(String message) {
    super(message);
  }
}
