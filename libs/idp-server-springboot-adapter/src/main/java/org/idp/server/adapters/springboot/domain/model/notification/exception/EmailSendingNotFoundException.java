package org.idp.server.adapters.springboot.domain.model.notification.exception;

import org.idp.server.adapters.springboot.domain.model.base.NotFoundException;

public class EmailSendingNotFoundException extends NotFoundException {

  public EmailSendingNotFoundException(String message) {
    super(message);
  }
}
