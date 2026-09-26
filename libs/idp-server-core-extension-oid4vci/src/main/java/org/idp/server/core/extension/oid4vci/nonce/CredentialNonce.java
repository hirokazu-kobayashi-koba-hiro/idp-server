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
package org.idp.server.core.extension.oid4vci.nonce;

import java.time.LocalDateTime;

/**
 * A {@code c_nonce} handed out by the Nonce Endpoint (OpenID4VCI 1.0 Section 7).
 *
 * <p>Spent by the Credential Request that carries it (Section 13.8 "Proof replay": "The Credential
 * Issuer determines for how long a particular nonce can be used"), so a key proof cannot be
 * replayed to obtain a duplicate Credential. Unlike the ABCA challenge, reuse buys nothing here: a
 * Wallet fetches a new nonce before each request.
 */
public class CredentialNonce {

  String value;
  LocalDateTime expiresAt;
  LocalDateTime createdAt;

  public CredentialNonce(String value, LocalDateTime expiresAt, LocalDateTime createdAt) {
    this.value = value;
    this.expiresAt = expiresAt;
    this.createdAt = createdAt;
  }

  public String value() {
    return value;
  }

  public LocalDateTime expiresAt() {
    return expiresAt;
  }

  public LocalDateTime createdAt() {
    return createdAt;
  }
}
