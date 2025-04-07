package org.idp.server.core.basic.http;

public class HttpRequestUrl {
  String value;

  public HttpRequestUrl() {}

  public HttpRequestUrl(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
