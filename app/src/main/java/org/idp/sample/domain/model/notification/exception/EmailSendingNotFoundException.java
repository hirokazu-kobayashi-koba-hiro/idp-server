package org.idp.sample.domain.model.notification.exception;

import org.idp.sample.domain.model.base.NotFoundException;

public class EmailSendingNotFoundException extends NotFoundException {

  public EmailSendingNotFoundException(String message) {
    super(message);
  }
}
