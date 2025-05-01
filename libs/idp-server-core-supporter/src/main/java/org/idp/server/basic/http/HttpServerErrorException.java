package org.idp.server.basic.http;

public class HttpServerErrorException extends RuntimeException {
  int statusCode;

  public HttpServerErrorException(String message, int statusCode) {
    super(message);
  }

  public int statusCode() {
    return statusCode;
  }
}
