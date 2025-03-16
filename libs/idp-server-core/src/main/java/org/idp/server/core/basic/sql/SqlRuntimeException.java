package org.idp.server.core.basic.sql;

public class SqlRuntimeException extends RuntimeException {

  public SqlRuntimeException(String message) {
    super(message);
  }

  public SqlRuntimeException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
