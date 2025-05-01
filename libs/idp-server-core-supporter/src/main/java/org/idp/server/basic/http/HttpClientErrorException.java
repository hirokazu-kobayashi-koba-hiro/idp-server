package org.idp.server.basic.http;

public class HttpClientErrorException extends RuntimeException {

  int statusCode;

  public HttpClientErrorException(String message, int statusCode) {
    super(message);
  }

  public int statusCode() {
    return statusCode;
  }
}
