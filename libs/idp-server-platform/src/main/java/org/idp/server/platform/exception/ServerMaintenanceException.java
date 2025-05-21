package org.idp.server.platform.exception;

public class ServerMaintenanceException extends RuntimeException {
  public ServerMaintenanceException(String message) {
    super(message);
  }

  public ServerMaintenanceException(String message, Throwable cause) {
    super(message, cause);
  }
}
