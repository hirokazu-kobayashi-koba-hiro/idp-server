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

package org.idp.server.core.openid.oauth.type.oauth;

import java.util.Objects;

/**
 * The resource indicator a token is issued for, carried as the {@code aud} claim.
 *
 * <p>It names the resource server, not the client: the client is named by {@code client_id}. A
 * resource server rejects a token whose audience is not one of its own identifiers, which is what
 * stops a token minted for one resource from being replayed against another.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9068.html#section-2.2">RFC 9068 Section 2.2</a>
 */
public class Audience {

  String value;

  public Audience() {}

  public Audience(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  @Override
  public boolean equals(Object object) {
    if (object == null || getClass() != object.getClass()) return false;
    Audience audience = (Audience) object;
    return Objects.equals(value, audience.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
