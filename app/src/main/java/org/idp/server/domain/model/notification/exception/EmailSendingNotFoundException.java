package org.idp.server.domain.model.notification.exception;

import org.idp.server.domain.model.base.NotFoundException;

public class EmailSendingNotFoundException extends NotFoundException {

  public EmailSendingNotFoundException(String message) {
    super(message);
  }
}
