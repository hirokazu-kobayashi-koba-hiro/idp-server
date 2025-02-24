package org.idp.sample.domain.model.base;

public class ServerMaintenanceException extends RuntimeException {
  public ServerMaintenanceException(String message) {
    super(message);
  }

  public ServerMaintenanceException(String message, Throwable cause) {
    super(message, cause);
  }
}
