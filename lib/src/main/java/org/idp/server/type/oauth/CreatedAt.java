package org.idp.server.type.oauth;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

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

  public long toEpochSecondWithUtc() {
    return value.toEpochSecond(ZoneOffset.UTC);
  }
}
