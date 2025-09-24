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

package org.idp.server.notification.push.apns;

import java.time.LocalDateTime;
import java.util.Objects;
import org.idp.server.platform.date.SystemDateTime;

public class JwtTokenCache {

  private final String token;
  private final LocalDateTime expiresAt;

  public JwtTokenCache(String token, LocalDateTime expiresAt) {
    this.token = token;
    this.expiresAt = expiresAt;
  }

  public String token() {
    return token;
  }

  public boolean isExpired() {
    return SystemDateTime.now().isAfter(expiresAt);
  }

  public boolean shouldRefresh() {
    // Refresh 5 minutes before expiration (55 minutes after creation for 1-hour tokens)
    return SystemDateTime.now().isAfter(expiresAt.minusSeconds(300));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    JwtTokenCache that = (JwtTokenCache) o;
    return Objects.equals(token, that.token) && Objects.equals(expiresAt, that.expiresAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(token, expiresAt);
  }
}
