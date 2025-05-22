/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.idp.server.basic.type.extension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** ExpiredAt */
public class ExpiredAt {
  LocalDateTime value;

  public ExpiredAt() {}

  public ExpiredAt(LocalDateTime value) {
    this.value = value;
  }

  public ExpiredAt(String value) {
    this.value = LocalDateTime.parse(value);
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

  public String toStringValue() {
    return value.toString();
  }
}
