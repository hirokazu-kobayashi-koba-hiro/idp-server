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

package org.idp.server.platform.jose;

import com.nimbusds.jwt.JWTClaimsSet;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * JsonWebTokenClaims
 *
 * <p><b>{@code hasXxx()} semantics (#1776):</b> the typed presence checks report {@code true} only
 * when the claim resolves to a non-null value, not merely when the key is present. Nimbus parses a
 * claim whose JSON value is {@code null} without rejecting it — {@code
 * getClaims().containsKey(key)} returns {@code true} while the typed getter returns {@code null}.
 * Basing {@code hasXxx()} on key presence alone let callers pass the {@code if (!claims.hasXxx())
 * throw ...} guard and then dereference a {@code null} getter (NPE → 500), reachable
 * unauthenticated via a self-signed DPoP proof. Checking the typed value closes every {@code
 * guard-then-get} site at once. The generic {@link #contains(String)} keeps key-presence semantics
 * for non-typed claims (htm/htu/scope); their values are read through {@link #getValue(String)},
 * which absorbs null and non-string values so those callers stay on the same safe path.
 */
public class JsonWebTokenClaims {
  JWTClaimsSet value;

  public JsonWebTokenClaims() {}

  public JsonWebTokenClaims(JWTClaimsSet value) {
    this.value = value;
  }

  public String getIss() {
    return value.getIssuer();
  }

  public boolean hasIss() {
    return value != null && value.getIssuer() != null;
  }

  public String getSub() {
    return value.getSubject();
  }

  public boolean hasSub() {
    return value != null && value.getSubject() != null;
  }

  public List<String> getAud() {
    return value.getAudience();
  }

  public boolean hasAud() {
    return value != null && value.getAudience() != null && !value.getAudience().isEmpty();
  }

  public Date getNbf() {
    return value.getNotBeforeTime();
  }

  public boolean hasNbf() {
    return value != null && value.getNotBeforeTime() != null;
  }

  public Date getIat() {
    return value.getIssueTime();
  }

  public boolean hasIat() {
    return value != null && value.getIssueTime() != null;
  }

  public String getJti() {
    return value.getJWTID();
  }

  public boolean hasJti() {
    return value != null && value.getJWTID() != null;
  }

  public Date getExp() {
    return value.getExpirationTime();
  }

  public boolean hasExp() {
    return value != null && value.getExpirationTime() != null;
  }

  public Map<String, Object> payload() {
    return value.getClaims();
  }

  /**
   * Returns the claim as a string, or {@code ""} when it is absent, null-valued, or not a string.
   *
   * <p>The claim value type is attacker-controlled: a JWT may carry {@code "htm": 123} or {@code
   * "scope": ["a","b"]} just as easily as a string. Casting blindly raised a {@link
   * ClassCastException} — an uncaught {@code RuntimeException} surfacing as 500 — through the same
   * unauthenticated, self-signed DPoP proof path as the null-valued-claim NPE of #1776. Non-string
   * values are therefore reported as absent, which every caller already handles (they test for
   * {@code isEmpty()} or compare against an expected value).
   *
   * @param key the claim name
   * @return the string claim value, or {@code ""} if absent, null, or not a string
   */
  public String getValue(String key) {
    if (!contains(key)) {
      return "";
    }
    Object claimValue = payload().get(key);
    if (!(claimValue instanceof String)) {
      return "";
    }
    return (String) claimValue;
  }

  public boolean contains(String key) {
    if (Objects.isNull(value)) {
      return false;
    }
    return value.getClaims().containsKey(key);
  }

  public boolean exists() {
    if (Objects.isNull(value) || Objects.isNull(value.getClaims())) {
      return false;
    }
    return !value.getClaims().isEmpty();
  }

  public Map<String, Object> toMap() {
    return value.getClaims();
  }
}
