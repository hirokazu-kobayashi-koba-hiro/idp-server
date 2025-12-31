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

import java.net.URI;
import java.util.Objects;

public class UriWrapper {
  URI value;

  public UriWrapper(URI value) {
    this.value = value;
  }

  public UriWrapper(String value) throws InvalidUriException {
    try {
      this.value = new URI(value);
    } catch (Exception exception) {
      throw new InvalidUriException(exception);
    }
  }

  public int getPort() {
    int port = value.getPort();
    if (port != -1) {
      return port;
    }
    // RFC 3986 Section 3.1: Scheme is case-insensitive
    if (value.getScheme().equalsIgnoreCase("https")) {
      return 443;
    }
    if (value.getScheme().equalsIgnoreCase("http")) {
      return 80;
    }
    return -1;
  }

  public boolean equalsUserinfo(UriWrapper other) {
    if (!hasUserinfo() && !other.hasUserinfo()) {
      return true;
    }
    if (hasUserinfo() && !other.hasUserinfo()) {
      return false;
    }
    if (!hasUserinfo() && other.hasUserinfo()) {
      return false;
    }
    return getUserinfo().equals(other.getUserinfo());
  }

  public boolean equalsPath(UriWrapper other) {
    if (!hasPath() && !other.hasPath()) {
      return true;
    }
    if (hasPath() && !other.hasPath()) {
      return false;
    }
    if (!hasPath() && other.hasPath()) {
      return false;
    }
    return getPath().equals(other.getPath());
  }

  public boolean equalsHost(UriWrapper other) {
    return getHost().equalsIgnoreCase(other.getHost());
  }

  public boolean equalsPort(UriWrapper other) {
    return getPort() == other.getPort();
  }

  /**
   * RFC 3986 Section 3.1: Scheme is case-insensitive
   *
   * @param other the other UriWrapper to compare
   * @return true if schemes are equal (case-insensitive)
   */
  public boolean equalsScheme(UriWrapper other) {
    if (!hasScheme() && !other.hasScheme()) {
      return true;
    }
    if (hasScheme() && !other.hasScheme()) {
      return false;
    }
    if (!hasScheme() && other.hasScheme()) {
      return false;
    }
    return getScheme().equalsIgnoreCase(other.getScheme());
  }

  public String getScheme() {
    return value.getScheme();
  }

  public boolean hasScheme() {
    return Objects.nonNull(value.getScheme());
  }

  public String getHost() {
    return value.getHost();
  }

  public String getUserinfo() {
    return value.getUserInfo();
  }

  public boolean hasUserinfo() {
    return Objects.nonNull(value.getUserInfo());
  }

  public String getPath() {
    return value.getPath();
  }

  public boolean hasPath() {
    return Objects.nonNull(value.getPath());
  }

  public boolean hasFragment() {
    return Objects.nonNull(value.getFragment());
  }

  /**
   * RFC 6749 Section 3.1.2: redirect_uri MUST be absolute URI
   *
   * @return true if URI is absolute (has scheme)
   */
  public boolean isAbsolute() {
    return value.isAbsolute();
  }

  /**
   * RFC 8252 Section 7.3: Loopback Interface Redirection
   *
   * <p>Native apps can use http://localhost or http://127.0.0.1 for redirect_uri. The port can be
   * dynamic.
   *
   * @return true if host is localhost or 127.0.0.1
   */
  public boolean isLoopbackHost() {
    String host = getHost();
    if (host == null) {
      return false;
    }
    return host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1");
  }

  /**
   * RFC 8252 Section 7.3: Loopback port matching
   *
   * <p>For loopback URIs, the port can be dynamic. This method compares two URIs ignoring port.
   *
   * @param other the other UriWrapper to compare
   * @return true if URIs match ignoring port (for loopback)
   */
  public boolean equalsIgnoringPort(UriWrapper other) {
    if (!equalsScheme(other)) {
      return false;
    }
    if (!equalsHost(other)) {
      return false;
    }
    if (!equalsPath(other)) {
      return false;
    }
    return equalsUserinfo(other);
  }

  public boolean isHttpScheme() {
    return hasScheme() && getScheme().equalsIgnoreCase("http");
  }

  public boolean isHttpsScheme() {
    return hasScheme() && getScheme().equalsIgnoreCase("https");
  }

  public boolean isCustomScheme() {
    if (!hasScheme()) {
      return false;
    }
    String scheme = getScheme().toLowerCase();
    return !scheme.equals("http") && !scheme.equals("https");
  }
}
