package org.idp.server.adapters.springboot.domain.model.notification.exception;

import org.idp.server.adapters.springboot.domain.model.base.UnauthorizedException;

public class EmailSendingUnauthorizedException extends UnauthorizedException {
  public EmailSendingUnauthorizedException(String message) {
    super(message);
  }
}
