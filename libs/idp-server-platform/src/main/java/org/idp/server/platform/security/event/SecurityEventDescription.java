package org.idp.server.platform.security.event;

public class SecurityEventDescription {

  String value;

  public SecurityEventDescription() {}

  public SecurityEventDescription(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
