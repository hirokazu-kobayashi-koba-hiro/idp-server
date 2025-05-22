package org.idp.server.platform.notification.exception;

import org.idp.server.platform.exception.NotFoundException;

public class EmailSendingNotFoundException extends NotFoundException {

  public EmailSendingNotFoundException(String message) {
    super(message);
  }
}
