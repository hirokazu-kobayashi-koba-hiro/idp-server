package org.idp.server.core.security.event;

import java.time.LocalDateTime;

public class SecurityEventDatetime {

  LocalDateTime value;

  public SecurityEventDatetime() {}

  public SecurityEventDatetime(LocalDateTime value) {
    this.value = value;
  }

  public LocalDateTime value() {
    return value;
  }

  public String valueAsString() {
    return value.toString();
  }
}
