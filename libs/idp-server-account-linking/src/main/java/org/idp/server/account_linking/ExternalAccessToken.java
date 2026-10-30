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
package org.idp.server.account_linking;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * An access token the external IdP issued for a linked account, decrypted for use against its API.
 *
 * <p>Only the access token is ever opened for a caller. The refresh token stays encrypted in the
 * stored account.
 */
public class ExternalAccessToken {

  static final String ISSUED_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";

  String value;
  LocalDateTime expiresAt;
  String scope;

  public ExternalAccessToken(String value, LocalDateTime expiresAt, String scope) {
    this.value = value;
    this.expiresAt = expiresAt;
    this.scope = scope;
  }

  public String value() {
    return value;
  }

  public LocalDateTime expiresAt() {
    return expiresAt;
  }

  public boolean hasExpiresAt() {
    return expiresAt != null;
  }

  /** Seconds left until the token expires, never negative. */
  public long expiresIn(LocalDateTime now) {
    return Math.max(Duration.between(now, expiresAt).getSeconds(), 0);
  }

  /**
   * The body handed to the client, shaped as a token exchange response (RFC 8693 §2.2.1).
   *
   * <p>Only the access token goes out. The refresh token stays here, where it is spent: a client
   * spending it would leave the stored one revoked by a provider that rotates them. {@code
   * expires_in} counts from {@code now}, and {@code scope} is there when the link kept one.
   */
  public Map<String, Object> toResponse(LocalDateTime now) {
    Map<String, Object> contents = new HashMap<>();
    contents.put("access_token", value);
    contents.put("token_type", "Bearer");
    contents.put("issued_token_type", ISSUED_TOKEN_TYPE);
    if (hasExpiresAt()) {
      contents.put("expires_in", expiresIn(now));
    }
    if (hasScope()) {
      contents.put("scope", scope);
    }
    return contents;
  }

  public String scope() {
    return scope;
  }

  public boolean hasScope() {
    return scope != null && !scope.isEmpty();
  }
}
