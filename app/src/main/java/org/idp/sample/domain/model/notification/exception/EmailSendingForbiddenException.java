package org.idp.sample.domain.model.notification.exception;

import org.idp.sample.domain.model.base.ForbiddenException;

public class EmailSendingForbiddenException extends ForbiddenException {
  public EmailSendingForbiddenException(String message) {
    super(message);
  }
}
