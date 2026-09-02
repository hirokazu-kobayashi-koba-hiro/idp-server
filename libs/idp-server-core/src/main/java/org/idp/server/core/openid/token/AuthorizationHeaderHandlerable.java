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

public interface AuthorizationHeaderHandlerable {

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

  default BasicAuth convertBasicAuth(String authorizationHeader) {
    if (!isBasicAuth(authorizationHeader)) {
      return new BasicAuth();
    }
    String value = authorizationHeader.substring("Basic ".length());
    try {
      byte[] decode = Base64.getUrlDecoder().decode(value);
      String decodedValue = new String(decode, StandardCharsets.UTF_8);
      if (!decodedValue.contains(":")) {
        return new BasicAuth();
      }
      String[] splitValues = decodedValue.split(":", 2);
      if (splitValues.length < 2) {
        return new BasicAuth();
      }
      return new BasicAuth(formUrlDecode(splitValues[0]), formUrlDecode(splitValues[1]));
    } catch (IllegalArgumentException e) {
      return new BasicAuth();
    }
  }

  /**
   * Reverses the {@code application/x-www-form-urlencoded} encoding that RFC 6749 Section 2.3.1
   * requires the client to apply to its identifier and password before Basic authentication.
   *
   * <p>The encoding is what makes the Basic credential unambiguous: a colon inside the identifier
   * or the password is carried as {@code %3A}, so exactly one literal colon separates the two
   * halves and {@code split(":", 2)} is always correct. Without decoding, a client that follows the
   * specification fails authentication, and a {@code client_id_alias} containing a colon resolves
   * to a different (truncated) client identifier instead.
   *
   * <p>Per RFC 6749 Appendix B this is HTML form encoding over UTF-8, so {@code +} denotes a space
   * and a literal plus sign arrives as {@code %2B} -- the same algorithm as {@link URLDecoder}.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-2.3.1">RFC 6749 Section 2.3.1</a>
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#appendix-B">RFC 6749 Appendix B</a>
   */
  private static String formUrlDecode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
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
