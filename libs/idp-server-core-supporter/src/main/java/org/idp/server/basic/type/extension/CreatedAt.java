package org.idp.server.basic.type.extension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** CreatedAt */
public class CreatedAt {
  LocalDateTime value;

  public CreatedAt() {}

  public CreatedAt(LocalDateTime value) {
    this.value = value;
  }

  public CreatedAt(String value) {
    this.value = LocalDateTime.parse(value);
  }

  public LocalDateTime value() {
    return value;
  }

  public long toEpochSecondWithUtc() {
    return value.toEpochSecond(ZoneOffset.UTC);
  }

  public String toStringValue() {
    return value.toString();
  }
}
