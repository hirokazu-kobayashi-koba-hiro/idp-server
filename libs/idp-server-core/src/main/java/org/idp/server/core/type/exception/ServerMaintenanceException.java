package org.idp.server.core.type.exception;

public class ServerMaintenanceException extends RuntimeException {
  public ServerMaintenanceException(String message) {
    super(message);
  }

  public ServerMaintenanceException(String message, Throwable cause) {
    super(message, cause);
  }
}
