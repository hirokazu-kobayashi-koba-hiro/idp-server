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
   * <p>RFC 7617 encodes {@code user-id ":" password} with standard Base64 (RFC 4648 Section 4), so
   * the standard alphabet is tried first. The URL-safe alphabet was used here historically, which
   * rejected {@code +} and {@code /} -- roughly one of every 32 characters of a Base64-encoded
   * secret -- and the resulting {@link IllegalArgumentException} was swallowed, so conformant
   * credentials failed authentication with no diagnostic. The platform module was corrected the
   * same way in #1245; this is the core-side follow-up.
   *
   * <p><b>Transitional fallback.</b> Switching outright would swap which population breaks: a
   * client that (incorrectly) encodes with the URL-safe alphabet authenticates today and would stop
   * doing so. Both alphabets are therefore accepted for now. There is no ambiguity between them --
   * a string containing {@code + /} decodes only as standard, one containing {@code - _} only as
   * URL-safe, and a string with neither is byte-identical in both -- so acceptance stays
   * deterministic. Every fallback is logged at ERROR naming the user-id -- the level is deliberate:
   * this is a migration signal that has to stand out rather than settle into the warning noise --
   * so the remaining callers can be identified before the fallback is removed and this becomes
   * standard-only.
   *
   * <p>{@code BasicAuthConvertable} in the platform module switched to the standard alphabet
   * outright in #1245 and has needed no such fallback. That is not a precedent for doing the same
   * here: it is reached only from the identity-verification callback and pre-hook, whose callers
   * are a handful of operator-managed services, whereas this method sits in front of every OAuth
   * client. The population that a hard switch could break is of a different order, hence the staged
   * migration.
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
    boolean decodedWithUrlSafeAlphabet = false;
    try {
      decodedValue = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      try {
        decodedValue = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        decodedWithUrlSafeAlphabet = true;
      } catch (IllegalArgumentException ignored) {
        return new BasicAuth();
      }
    }
    if (!decodedValue.contains(":")) {
      return new BasicAuth();
    }
    String[] splitValues = decodedValue.split(":", 2);
    if (splitValues.length < 2) {
      return new BasicAuth();
    }
    if (decodedWithUrlSafeAlphabet) {
      log.error(
          "Basic credential decoded with the URL-safe Base64 alphabet. RFC 7617 requires standard"
              + " Base64 (RFC 4648 Section 4); this fallback is transitional and will be removed."
              + " user-id={}",
          forLog(splitValues[0]));
    }
    return new BasicAuth(splitValues[0], splitValues[1]);
  }

  /**
   * Renders an untrusted credential half for a log line: control characters (a raw CR/LF would let
   * the value forge additional log entries) are dropped and the result is truncated. Only the
   * user-id is ever passed here -- the password must not reach the log.
   */
  private static String forLog(String value) {
    String sanitized = value.replaceAll("\\p{Cntrl}", "");
    return sanitized.length() > 64 ? sanitized.substring(0, 64) + "..." : sanitized;
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
