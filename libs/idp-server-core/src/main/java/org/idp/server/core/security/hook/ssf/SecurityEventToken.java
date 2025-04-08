package org.idp.server.core.security.hook.ssf;

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
