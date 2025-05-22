/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
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
