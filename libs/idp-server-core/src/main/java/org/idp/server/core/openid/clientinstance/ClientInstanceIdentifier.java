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

package org.idp.server.core.openid.clientinstance;

import java.util.Objects;

/**
 * Identifier of a Client Instance.
 *
 * <p>Matches the {@code kid} JOSE header parameter of a self-signed Client Attestation JWT: the
 * value selects which registered Client Instance Key to verify the signature with. It is a lookup
 * key only — never a trust input (see {@code ClientAttestationKeyResolver}).
 */
public class ClientInstanceIdentifier {

  String value;

  public ClientInstanceIdentifier() {}

  public ClientInstanceIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isBlank();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    ClientInstanceIdentifier that = (ClientInstanceIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
