package org.idp.server.core.authentication.notification.exception;

import org.idp.server.basic.exception.ServerMaintenanceException;

public class EmailSendingMaintenanceException extends ServerMaintenanceException {

  public EmailSendingMaintenanceException(String message) {
    super(message);
  }

  public EmailSendingMaintenanceException(String message, Throwable cause) {
    super(message, cause);
  }
}
