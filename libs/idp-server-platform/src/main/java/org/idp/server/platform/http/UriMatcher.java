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

public class UriMatcher {

  public static boolean matchWithNormalizationAndComparison(String target, String other) {
    if (equalsSimpleComparison(target, other)) {
      return true;
    }
    return matchSyntaxBasedNormalization(target, other);
  }

  /**
   * Returns the comparison result type for detailed error messaging.
   *
   * @param target the registered redirect_uri
   * @param other the requested redirect_uri
   * @return UriComparisonResult indicating match type or mismatch reason
   */
  public static UriComparisonResult compareWithDetails(String target, String other) {
    if (equalsSimpleComparison(target, other)) {
      return UriComparisonResult.SIMPLE_MATCH;
    }
    if (matchSyntaxBasedNormalization(target, other)) {
      return UriComparisonResult.NORMALIZED_MATCH;
    }
    return UriComparisonResult.NO_MATCH;
  }

  static boolean equalsSimpleComparison(String target, String other) {
    return target.equals(other);
  }

  /**
   * RFC 3986 Section 6.2.2: Syntax-Based Normalization
   *
   * <p>Compares URIs with case normalization for scheme and host components.
   *
   * <ul>
   *   <li>Scheme: case-insensitive (RFC 3986 Section 3.1)
   *   <li>Host: case-insensitive (RFC 3986 Section 3.2.2)
   *   <li>Port: default port normalization (443 for https, 80 for http)
   *   <li>Path: case-sensitive (RFC 3986 Section 3.3)
   *   <li>Userinfo: case-sensitive (RFC 3986 Section 3.2.1)
   * </ul>
   *
   * @param target the registered redirect_uri
   * @param other the requested redirect_uri
   * @return true if URIs match after syntax-based normalization
   */
  static boolean matchSyntaxBasedNormalization(String target, String other) {
    try {
      UriWrapper targetUri = new UriWrapper(new URI(target));
      UriWrapper otherUri = new UriWrapper(new URI(other));
      if (!targetUri.equalsScheme(otherUri)) {
        return false;
      }
      if (!targetUri.equalsUserinfo(otherUri)) {
        return false;
      }
      if (!targetUri.equalsHost(otherUri)) {
        return false;
      }
      if (!targetUri.equalsPort(otherUri)) {
        return false;
      }
      return targetUri.equalsPath(otherUri);
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * RFC 8252 Section 7.3: Loopback Interface Redirection
   *
   * <p>For native apps using loopback redirect URIs (localhost/127.0.0.1), the port can be dynamic.
   * This method matches URIs ignoring port for loopback addresses.
   *
   * @param target the registered redirect_uri
   * @param other the requested redirect_uri
   * @return true if URIs match (with dynamic port allowed for loopback)
   */
  public static boolean matchWithLoopbackPortAllowance(String target, String other) {
    if (equalsSimpleComparison(target, other)) {
      return true;
    }
    try {
      UriWrapper targetUri = new UriWrapper(new URI(target));
      UriWrapper otherUri = new UriWrapper(new URI(other));

      // Only allow dynamic port for loopback addresses
      if (!targetUri.isLoopbackHost() || !otherUri.isLoopbackHost()) {
        return matchSyntaxBasedNormalization(target, other);
      }

      // For loopback, compare everything except port
      return targetUri.equalsIgnoringPort(otherUri);
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Validates if the URI is an absolute URI.
   *
   * <p>RFC 6749 Section 3.1.2: redirect_uri MUST be absolute URI
   *
   * @param uri the URI string to validate
   * @return true if the URI is absolute
   */
  public static boolean isAbsoluteUri(String uri) {
    try {
      UriWrapper wrapper = new UriWrapper(new URI(uri));
      return wrapper.isAbsolute();
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Checks if the URI is a loopback address.
   *
   * @param uri the URI string to check
   * @return true if the URI host is localhost or 127.0.0.1
   */
  public static boolean isLoopbackUri(String uri) {
    try {
      UriWrapper wrapper = new UriWrapper(new URI(uri));
      return wrapper.isLoopbackHost();
    } catch (Exception e) {
      return false;
    }
  }
}
