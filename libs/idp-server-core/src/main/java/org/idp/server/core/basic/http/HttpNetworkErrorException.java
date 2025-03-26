package org.idp.server.core.basic.http;

public class HttpNetworkErrorException extends RuntimeException {
  public HttpNetworkErrorException(String message, Throwable cause) {
    super(message, cause);
  }
}
