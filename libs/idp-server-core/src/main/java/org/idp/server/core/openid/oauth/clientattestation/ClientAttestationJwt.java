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

package org.idp.server.core.openid.oauth.clientattestation;

import java.util.Objects;

/**
 * Client Attestation JWT value object.
 *
 * <p>Represents the raw Client Attestation JWT string received in the {@code
 * OAuth-Client-Attestation} HTTP request header field. The JWT is issued by a Client Attester and
 * binds the Client Instance Key ({@code cnf.jwk}) to the client ({@code sub}).
 *
 * @see <a
 *     href="https://www.ietf.org/archive/id/draft-ietf-oauth-attestation-based-client-auth-10.html">OAuth
 *     2.0 Attestation-Based Client Authentication</a>
 */
public class ClientAttestationJwt {

  /** HTTP request header field name conveying the Client Attestation JWT. */
  public static final String HEADER_NAME = "OAuth-Client-Attestation";

  String value;

  public ClientAttestationJwt() {}

  public ClientAttestationJwt(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isBlank();
  }
}
