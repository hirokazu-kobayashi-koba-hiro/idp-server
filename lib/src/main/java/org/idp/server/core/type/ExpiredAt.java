package org.idp.server.core.type;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** ExpiredAt */
public class ExpiredAt {
  LocalDateTime value;

  public ExpiredAt() {}

  public ExpiredAt(LocalDateTime value) {
    this.value = value;
  }

  public LocalDateTime value() {
    return value;
  }

  public boolean isExpire(LocalDateTime other) {
    return value.isBefore(other);
  }

  public long toEpochSecondWithUtc() {
    return value.toEpochSecond(ZoneOffset.UTC);
  }
}
