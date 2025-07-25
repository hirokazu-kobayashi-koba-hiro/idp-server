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

package org.idp.server.core.oidc.type.oauth;

import java.util.Objects;

/**
 * ClientIdentifier
 *
 * <p>The authorization server issues the registered client a client identifier -- a unique string
 * representing the registration information provided by the client. The client identifier is not a
 * secret; it is exposed to the resource owner and MUST NOT be used alone for client authentication.
 * The client identifier is unique to the authorization server.
 *
 * <p>The client identifier string size is left undefined by this specification. The client should
 * avoid making assumptions about the identifier size. The authorization server SHOULD document the
 * size of any identifier it issues.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-2.2">2.2. Client Identifier</a>
 */
public class RequestedClientId {
  String value;

  public RequestedClientId() {}

  public RequestedClientId(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RequestedClientId requestedClientId = (RequestedClientId) o;
    return Objects.equals(value, requestedClientId.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
