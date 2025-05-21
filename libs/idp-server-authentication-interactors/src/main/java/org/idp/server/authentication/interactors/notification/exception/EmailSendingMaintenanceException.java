package org.idp.server.authentication.interactors.notification.exception;

import org.idp.server.platform.exception.ServerMaintenanceException;

public class EmailSendingMaintenanceException extends ServerMaintenanceException {

  public EmailSendingMaintenanceException(String message) {
    super(message);
  }

  public EmailSendingMaintenanceException(String message, Throwable cause) {
    super(message, cause);
  }
}
