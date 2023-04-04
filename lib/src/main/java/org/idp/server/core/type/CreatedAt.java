package org.idp.server.core.type;

import java.time.LocalDateTime;

/** CreatedAt */
public class CreatedAt {
  LocalDateTime value;

  public CreatedAt() {}

  public CreatedAt(LocalDateTime value) {
    this.value = value;
  }

  public LocalDateTime value() {
    return value;
  }

  public boolean isExpire(LocalDateTime other) {
    return value.isBefore(other);
  }
}
