package org.idp.server.core.type;

import java.time.LocalDateTime;

/** ExpiresDateTime */
public class ExpiresDateTime {
  LocalDateTime value;

  public ExpiresDateTime() {}

  public ExpiresDateTime(LocalDateTime value) {
    this.value = value;
  }

  public LocalDateTime value() {
    return value;
  }

  public boolean isExpire(LocalDateTime other) {
    return value.isBefore(other);
  }
}
