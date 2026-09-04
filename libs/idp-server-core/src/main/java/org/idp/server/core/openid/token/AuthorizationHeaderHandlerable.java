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

package org.idp.server.core.openid.token;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.idp.server.core.openid.oauth.type.oauth.AccessTokenEntity;
import org.idp.server.platform.http.BasicAuth;
import org.idp.server.platform.log.LoggerWrapper;

public interface AuthorizationHeaderHandlerable {

  LoggerWrapper log = LoggerWrapper.getLogger(AuthorizationHeaderHandlerable.class);

  default AuthorizationHeaderType type(String authorizationHeader) {
    return AuthorizationHeaderType.of(authorizationHeader);
  }

  default boolean isBasicAuth(String authorizationHeader) {
    AuthorizationHeaderType type = type(authorizationHeader);
    return type.isBasic();
  }

  default boolean isBearer(String authorizationHeader) {
    AuthorizationHeaderType type = type(authorizationHeader);
    return type.isBearer();
  }

  default boolean isDPop(String authorizationHeader) {
    AuthorizationHeaderType type = type(authorizationHeader);
    return type.isDPoP();
  }

  /**
   * Parses an HTTP Basic {@code Authorization} header into its two halves.
   *
   * <p>RFC 7617 encodes {@code user-id ":" password} with standard Base64 (RFC 4648 Section 4), and
   * only that alphabet is accepted. The URL-safe alphabet was used here historically, which rejects
   * {@code +} and {@code /} -- roughly one of every 32 characters of a Base64-encoded secret -- and
   * the resulting {@link IllegalArgumentException} was swallowed, so conformant credentials failed
   * authentication with no diagnostic. {@code BasicAuthConvertable} in the platform module was
   * corrected the same way in #1245; this is the core-side follow-up.
   *
   * <p>Correcting this swaps which population fails: a client that (incorrectly) encodes with the
   * URL-safe alphabet authenticates today and stops doing so. That population is narrow -- the two
   * alphabets differ only at indexes 62 and 63, so an encoded credential is identical under both
   * unless it contains {@code + /} (equivalently {@code - _} in URL-safe form), which for ASCII
   * input requires {@code >}, {@code ?} or {@code ~} at a byte position of 3n+2. Widening the
   * accepted set to cover it was rejected: the fallback existed only to surface stragglers, yet it
   * had to run before client authentication on public endpoints, so any caller could emit an
   * unbounded number of log records with an arbitrary header.
   *
   * <p>The diagnostic is kept without accepting the credential. When standard decoding fails, the
   * URL-safe alphabet is tried purely to classify the failure, and the outcome is logged at DEBUG
   * without ever being returned. DEBUG is deliberate: this no longer signals a migration to chase,
   * only an explanation to reach for when an otherwise inexplicable {@code invalid_client} is being
   * investigated.
   *
   * <p>The credential is returned exactly as transmitted. Callers performing OAuth 2.0 client
   * authentication must use {@link #convertClientSecretBasicAuth(String)} instead, which
   * additionally reverses the encoding that RFC 6749 Section 2.3.1 requires of clients.
   *
   * @see <a href="https://datatracker.ietf.org/doc/html/rfc7617">RFC 7617</a>
   */
  default BasicAuth convertBasicAuth(String authorizationHeader) {
    if (!isBasicAuth(authorizationHeader)) {
      return new BasicAuth();
    }
    String value = authorizationHeader.substring("Basic ".length());
    String decodedValue;
    try {
      decodedValue = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      logIfUrlSafeAlphabet(value);
      return new BasicAuth();
    }
    if (!decodedValue.contains(":")) {
      return new BasicAuth();
    }
    String[] splitValues = decodedValue.split(":", 2);
    if (splitValues.length < 2) {
      return new BasicAuth();
    }
    return new BasicAuth(splitValues[0], splitValues[1]);
  }

  /**
   * Records why a credential that is not valid standard Base64 was rejected, when the reason is
   * that it was encoded with the URL-safe alphabet. Rejection has already been decided by the
   * caller -- this only classifies it, so that "the client sends base64url" is recoverable from the
   * logs instead of having to be guessed at from a bare {@code invalid_client}.
   *
   * <p>Nothing derived from the header is logged. The value is attacker-controlled and reaches this
   * method before any authentication, so emitting any part of it would let a caller write arbitrary
   * content into the log at will.
   */
  private static void logIfUrlSafeAlphabet(String value) {
    if (!log.isDebugEnabled()) {
      return;
    }
    try {
      Base64.getUrlDecoder().decode(value);
    } catch (IllegalArgumentException e) {
      return;
    }
    log.debug(
        "Basic credential rejected: encoded with the URL-safe Base64 alphabet. RFC 7617 requires"
            + " standard Base64 (RFC 4648 Section 4).");
  }

  /**
   * Parses a {@code client_secret_basic} credential, reversing the {@code
   * application/x-www-form-urlencoded} encoding that RFC 6749 Section 2.3.1 requires the client to
   * apply to its identifier and password.
   *
   * <p>The encoding is what makes the credential unambiguous: a colon inside either half is carried
   * as {@code %3A}, so exactly one literal colon separates them and {@code split(":", 2)} is always
   * correct. Without the decoding a specification-conformant client fails authentication, and a
   * {@code client_id_alias} containing a colon resolves to a different (truncated) client
   * identifier.
   *
   * <p>Per RFC 6749 Appendix B this is HTML form encoding over UTF-8, so {@code +} denotes a space
   * and a literal plus sign arrives as {@code %2B} -- the same algorithm as {@link URLDecoder}.
   *
   * <p>Deliberately separate from {@link #convertBasicAuth(String)}: Section 2.3.1 governs OAuth
   * client authentication at the token endpoint only. Applying it to other Basic-authenticated
   * surfaces (for example the management API, whose secret is generated with {@code base64} and can
   * therefore contain {@code +}) would silently rewrite a valid credential.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-2.3.1">RFC 6749 Section 2.3.1</a>
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#appendix-B">RFC 6749 Appendix B</a>
   */
  default BasicAuth convertClientSecretBasicAuth(String authorizationHeader) {
    BasicAuth basicAuth = convertBasicAuth(authorizationHeader);
    if (!basicAuth.exists()) {
      return basicAuth;
    }
    try {
      return new BasicAuth(
          URLDecoder.decode(basicAuth.username(), StandardCharsets.UTF_8),
          URLDecoder.decode(basicAuth.password(), StandardCharsets.UTF_8));
    } catch (IllegalArgumentException e) {
      return new BasicAuth();
    }
  }

  default AccessTokenEntity extractAccessToken(String authorizationHeader) {
    if (isBearer(authorizationHeader)) {
      String accessTokenValue =
          authorizationHeader.substring(AuthorizationHeaderType.Bearer.length());
      return new AccessTokenEntity(accessTokenValue);
    }
    if (isDPop(authorizationHeader)) {
      String accessTokenValue =
          authorizationHeader.substring(AuthorizationHeaderType.DPoP.length());
      return new AccessTokenEntity(accessTokenValue);
    }
    return new AccessTokenEntity();
  }
}
