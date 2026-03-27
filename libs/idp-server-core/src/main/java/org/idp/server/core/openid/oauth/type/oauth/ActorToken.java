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
 * ActorToken
 *
 * <p>RFC 8693 Section 2.1 - actor_token parameter.
 *
 * <blockquote>
 *
 * A security token that represents the identity of the acting party. Typically, this will be the
 * party that is authorized to use the requested security token and act on behalf of the subject.
 *
 * </blockquote>
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-2.1">RFC 8693 Section 2.1</a>
 */
public class ActorToken implements SecurityToken {

  String value;

  public ActorToken() {}

  public ActorToken(String value) {
    this.value = value;
  }

  @Override
  public String value() {
    return value;
  }

  @Override
  public boolean exists() {
    return value != null && !value.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    ActorToken that = (ActorToken) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
