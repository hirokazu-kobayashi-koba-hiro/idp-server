package org.idp.server.security.event.hook.ssf;

public class SecurityEventToken {

  String value;

  public SecurityEventToken() {}

  public SecurityEventToken(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
