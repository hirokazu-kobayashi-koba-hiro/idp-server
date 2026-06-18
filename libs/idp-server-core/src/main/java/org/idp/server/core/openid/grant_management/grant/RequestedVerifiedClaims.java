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

package org.idp.server.core.openid.grant_management.grant;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.idp.server.core.openid.identity.id_token.VerifiedClaimsObject;
import org.idp.server.platform.json.JsonConverter;

/**
 * The OIDC4IDA {@code verified_claims} request (from the {@code claims} parameter) persisted
 * alongside a grant's requested claim names, so it survives to ID Token / UserInfo issuance as part
 * of the consent record — the grant, not the transient request, is the authority for what was
 * granted. (#1628)
 *
 * <p>It is carried inside the existing space-delimited claim-name TEXT column as a single sentinel
 * token {@code vc:<base64url(JSON)>}, so no schema change is needed. Because claim emission matches
 * fixed known claim names (e.g. {@code values.contains("name")}) rather than enumerating the token
 * set, an unrecognized {@code vc:} token is simply ignored by code that predates this change —
 * keeping rolling deploys safe. The token is held verbatim and decoded lazily, so token reads that
 * never touch verified_claims (introspection, resource access) pay no parse cost.
 */
public class RequestedVerifiedClaims {

  static final String SENTINEL_PREFIX = "vc:";
  private static final JsonConverter jsonConverter = JsonConverter.defaultInstance();

  // "vc:<base64url(json)>" when a verified_claims request is present, otherwise null.
  private final String sentinelToken;
  private VerifiedClaimsObject cached;

  private RequestedVerifiedClaims(String sentinelToken) {
    this.sentinelToken = sentinelToken;
  }

  public static RequestedVerifiedClaims empty() {
    return new RequestedVerifiedClaims(null);
  }

  /** Builds from a parsed request; empty when there is no verified_claims to persist. */
  public static RequestedVerifiedClaims of(VerifiedClaimsObject verifiedClaims) {
    if (verifiedClaims == null) {
      return empty();
    }
    String json = jsonConverter.write(verifiedClaims);
    String base64 =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    return new RequestedVerifiedClaims(SENTINEL_PREFIX + base64);
  }

  /** True when {@code token} is the verified_claims sentinel rather than a plain claim name. */
  static boolean isSentinel(String token) {
    return token != null && token.startsWith(SENTINEL_PREFIX);
  }

  /** Restores from a serialized sentinel token; decoding is deferred to {@link #toObject()}. */
  static RequestedVerifiedClaims fromSentinel(String sentinelToken) {
    return new RequestedVerifiedClaims(sentinelToken);
  }

  public boolean exists() {
    return sentinelToken != null && !sentinelToken.isEmpty();
  }

  /** Decodes the persisted request lazily; an empty object when none was requested. */
  public VerifiedClaimsObject toObject() {
    if (!exists()) {
      return new VerifiedClaimsObject();
    }
    if (cached == null) {
      String base64 = sentinelToken.substring(SENTINEL_PREFIX.length());
      String json = new String(Base64.getUrlDecoder().decode(base64), StandardCharsets.UTF_8);
      cached = jsonConverter.read(json, VerifiedClaimsObject.class);
    }
    return cached;
  }

  /** The serialized sentinel token, or an empty string when absent (callers skip it). */
  public String toSentinelToken() {
    return exists() ? sentinelToken : "";
  }
}
