package org.idp.server.adapters.springboot.domain.model.notification.exception;

import org.idp.server.core.type.exception.ForbiddenException;

public class EmailSendingForbiddenException extends ForbiddenException {
  public EmailSendingForbiddenException(String message) {
    super(message);
  }
}
