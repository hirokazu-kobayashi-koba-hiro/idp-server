package org.idp.server.platform.datasource;

public class SqlTooManyResultsException extends RuntimeException {
  public SqlTooManyResultsException(String message) {
    super(message);
  }
}
