package org.idp.server.basic.datasource;

public class SqlTooManyResultsException extends RuntimeException {
  public SqlTooManyResultsException(String message) {
    super(message);
  }
}
