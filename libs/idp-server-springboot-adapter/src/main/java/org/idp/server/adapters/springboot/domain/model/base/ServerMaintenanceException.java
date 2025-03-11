package org.idp.server.adapters.springboot.domain.model.base;

public class ServerMaintenanceException extends RuntimeException {
  public ServerMaintenanceException(String message) {
    super(message);
  }

  public ServerMaintenanceException(String message, Throwable cause) {
    super(message, cause);
  }
}
