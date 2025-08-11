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

package org.idp.server.core.openid.oauth.type.extension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.idp.server.platform.date.LocalDateTimeParser;

/** ExpiresAt */
public class ExpiresAt {
  LocalDateTime value;

  public ExpiresAt() {}

  public ExpiresAt(LocalDateTime value) {
    this.value = value;
  }

  public ExpiresAt(String value) {
    this.value = LocalDateTimeParser.parse(value);
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

  public LocalDateTime toLocalDateTime() {
    return value;
  }

  public boolean isAfter(ExpiresAt other) {
    return value.isAfter(other.value());
  }
}
