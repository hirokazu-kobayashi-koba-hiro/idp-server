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
package org.idp.server.platform.oauth.client;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

/**
 * The values a client authentication type needs beyond {@code client_id} and {@code client_secret}
 * ({@code client_authentication} in a configuration).
 *
 * <p>Shared by every configuration that calls a token endpoint, so a federation and an {@code
 * oauth_authorization} take the same shape.
 */
public class OutboundClientAuthenticationConfig implements JsonReadable {

  /** The audience a client assertion is addressed to, when not set. */
  public static final String TOKEN_ENDPOINT_AUDIENCE = "token_endpoint";

  public static final String ISSUER_AUDIENCE = "issuer";

  String signingAlgorithm;
  Map<String, Object> signingKey;
  String assertionAudience;
  String issuer;

  public OutboundClientAuthenticationConfig() {}

  /**
   * The algorithm a client assertion is signed with: HS256, HS384 or HS512 for {@code
   * client_secret_jwt}, RS256, PS256, ES256 and the like for {@code private_key_jwt}.
   */
  public String signingAlgorithm() {
    return signingAlgorithm;
  }

  public boolean hasSigningAlgorithm() {
    return signingAlgorithm != null && !signingAlgorithm.isEmpty();
  }

  /**
   * The private key of {@code private_key_jwt}, as one JWK. Its {@code kid} goes in the assertion's
   * header.
   */
  public Map<String, Object> signingKey() {
    return signingKey == null ? Map.of() : signingKey;
  }

  public boolean hasSigningKey() {
    return signingKey != null && !signingKey.isEmpty();
  }

  /**
   * Whether the assertion is addressed to the authorization server's issuer rather than its token
   * endpoint.
   *
   * <p>RFC 7523 and OpenID Connect Core address it to the token endpoint, the default; FAPI 2.0 to
   * the issuer.
   */
  public boolean addressesIssuer() {
    return ISSUER_AUDIENCE.equals(assertionAudience);
  }

  public String assertionAudience() {
    return assertionAudience == null ? TOKEN_ENDPOINT_AUDIENCE : assertionAudience;
  }

  /**
   * The issuer to address the assertion to, for a configuration that does not carry the
   * authorization server's issuer itself.
   */
  public String issuer() {
    return issuer;
  }

  public boolean hasIssuer() {
    return issuer != null && !issuer.isEmpty();
  }

  public boolean exists() {
    return hasSigningAlgorithm() || hasSigningKey() || assertionAudience != null || hasIssuer();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    if (signingAlgorithm != null) map.put("signing_algorithm", signingAlgorithm);
    if (signingKey != null) map.put("signing_key", signingKey);
    if (assertionAudience != null) map.put("assertion_audience", assertionAudience);
    if (issuer != null) map.put("issuer", issuer);
    return map;
  }
}
