package org.idp.server.adapters.springboot.domain.model.notification.exception;

import org.idp.server.adapters.springboot.domain.model.base.ServerMaintenanceException;

public class EmailSendingMaintenanceException extends ServerMaintenanceException {

  public EmailSendingMaintenanceException(String message) {
    super(message);
  }

  public EmailSendingMaintenanceException(String message, Throwable cause) {
    super(message, cause);
  }
}
