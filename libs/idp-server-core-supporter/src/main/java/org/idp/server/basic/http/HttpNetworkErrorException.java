package org.idp.server.basic.http;

public class HttpNetworkErrorException extends RuntimeException {
  public HttpNetworkErrorException(String message, Throwable cause) {
    super(message, cause);
  }
}
