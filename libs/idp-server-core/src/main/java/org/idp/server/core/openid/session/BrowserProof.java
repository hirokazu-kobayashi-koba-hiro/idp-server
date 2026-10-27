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

package org.idp.server.core.openid.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.idp.server.platform.random.RandomStringGenerator;

/**
 * Secret handed to the browser that signed in, so a later request can tell its session cookie was
 * received by this browser rather than placed in it.
 *
 * <p>The session cookie is scoped to the tenant's path and carries no prefix, so a host on a
 * sibling subdomain can set one of its own in another browser. The session it points to is then a
 * real one, and nothing about the cookie tells the two apart. This secret goes out in a {@code
 * __Host-} cookie, which such a host cannot set, and only its hash is kept in the session.
 *
 * <p>The secret is not the session identifier. The cookie that carries it is sent on every path of
 * the host, other tenants included, and on its own it opens nothing.
 */
public class BrowserProof {

  private static final int LENGTH = 32;

  String value;

  BrowserProof(String value) {
    this.value = value;
  }

  public static BrowserProof generate() {
    return new BrowserProof(new RandomStringGenerator(LENGTH).generate());
  }

  public String value() {
    return value;
  }

  public String hash() {
    return SessionHashCalculator.sha256UrlEncodedHash(value);
  }

  /** Constant-time comparison of a presented secret against a stored hash. */
  public static boolean matches(String storedHash, String presentedValue) {
    if (storedHash == null
        || storedHash.isEmpty()
        || presentedValue == null
        || presentedValue.isEmpty()) {
      return false;
    }
    return MessageDigest.isEqual(
        storedHash.getBytes(StandardCharsets.UTF_8),
        SessionHashCalculator.sha256UrlEncodedHash(presentedValue)
            .getBytes(StandardCharsets.UTF_8));
  }
}
