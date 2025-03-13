package org.idp.server.adapters.springboot.domain.model.notification.exception;

import org.idp.server.core.type.exception.UnauthorizedException;

public class EmailSendingUnauthorizedException extends UnauthorizedException {
  public EmailSendingUnauthorizedException(String message) {
    super(message);
  }
}
