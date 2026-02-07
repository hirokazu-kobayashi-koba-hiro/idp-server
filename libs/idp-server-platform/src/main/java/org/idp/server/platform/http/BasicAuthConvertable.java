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

package org.idp.server.platform.http;

import java.util.Base64;

/**
 * Converts an HTTP Basic Authentication header into a {@link BasicAuth} credential pair.
 *
 * <p>Implements parsing according to RFC 7617. Uses standard Base64 decoding (not URL-safe) and
 * splits at the first colon, as user-id MUST NOT contain a colon character per the specification.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7617">RFC 7617 - The 'Basic' HTTP
 *     Authentication Scheme</a>
 */
public interface BasicAuthConvertable {

  /**
   * Parses an Authorization header with the Basic scheme.
   *
   * <p>The credential string is decoded using standard Base64 (RFC 4648 Section 4) and split into
   * user-id and password at the first colon. Passwords may contain colons; user-ids must not.
   *
   * @param authorizationHeader the Authorization header value (e.g. "Basic dXNlcjpwYXNz")
   * @return parsed {@link BasicAuth}, or an empty instance if the header is null, empty, not Basic
   *     scheme, or missing a colon separator
   */
  default BasicAuth convertBasicAuth(String authorizationHeader) {
    if (authorizationHeader == null || authorizationHeader.isEmpty()) {
      return new BasicAuth();
    }

    if (!authorizationHeader.startsWith("Basic ")) {
      return new BasicAuth();
    }

    String value = authorizationHeader.substring("Basic ".length());
    byte[] decode = Base64.getDecoder().decode(value);
    String decodedValue = new String(decode);
    if (!decodedValue.contains(":")) {
      return new BasicAuth();
    }
    String[] splitValues = decodedValue.split(":", 2);
    return new BasicAuth(splitValues[0], splitValues[1]);
  }
}
