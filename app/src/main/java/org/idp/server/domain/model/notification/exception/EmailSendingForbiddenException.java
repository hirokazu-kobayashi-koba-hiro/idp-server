package org.idp.server.domain.model.notification.exception;

import org.idp.server.domain.model.base.ForbiddenException;

public class EmailSendingForbiddenException extends ForbiddenException {
  public EmailSendingForbiddenException(String message) {
    super(message);
  }
}
