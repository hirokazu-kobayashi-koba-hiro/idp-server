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

package org.idp.server.core.openid.oauth.type.oauth;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;

/** RedirectUri */
public class RedirectUri {
  String value;

  public RedirectUri() {}

  public RedirectUri(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  /**
   * Whether {@code other} addresses the same place as this one.
   *
   * <p>Compared structurally rather than by prefix. A prefix test has no boundary, so a client
   * registered with a bare origin — {@code http://localhost:3000}, which plenty are — would accept
   * {@code http://localhost:3000.attacker.example}: it starts with the registered string while
   * pointing somewhere else entirely.
   *
   * <p>Scheme, host, port and path must match. Query and fragment are free, because that is where
   * the authorization response puts the code and state. Anything unusable — absent on either side,
   * or not a URI — is not the same place, which is also the safe answer.
   */
  public boolean addressesSameTarget(RedirectUri other) {
    if (!exists() || other == null || !other.exists()) {
      return false;
    }
    try {
      URI mine = new URI(value);
      URI theirs = new URI(other.value());
      // Userinfo is refused outright rather than compared. It is never part of a registered
      // redirect URI, and it is the part of a URL people misread: https://expected.example@host
      // shows the expected name while addressing host.
      if (theirs.getUserInfo() != null) {
        return false;
      }
      return Objects.equals(scheme(theirs), scheme(mine))
          && Objects.equals(host(theirs), host(mine))
          && theirs.getPort() == mine.getPort()
          && Objects.equals(path(theirs), path(mine));
    } catch (URISyntaxException e) {
      return false;
    }
  }

  private static String scheme(URI uri) {
    return uri.getScheme();
  }

  /** Host names are case-insensitive, so a differently cased one addresses the same place. */
  private static String host(URI uri) {
    String host = uri.getHost();
    return host == null ? null : host.toLowerCase(Locale.ROOT);
  }

  /** A missing path and a bare slash are the same place. */
  private static String path(URI uri) {
    String path = uri.getPath();
    return path == null || path.isEmpty() ? "/" : path;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RedirectUri that = (RedirectUri) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  public boolean isHttp() {
    return value.startsWith("http://");
  }

  public boolean isHttps() {
    return value.startsWith("https://");
  }
}
