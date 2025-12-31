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

package org.idp.server.core.openid.oauth.type.extension;

import java.util.Iterator;
import java.util.List;
import org.idp.server.platform.http.UriComparisonResult;
import org.idp.server.platform.http.UriMatcher;

public class RegisteredRedirectUris implements Iterable<String> {
  List<String> values;

  public RegisteredRedirectUris(List<String> values) {
    this.values = values;
  }

  public boolean contains(String other) {
    return values.contains(other);
  }

  public boolean containsWithNormalizationAndComparison(String other) {
    return values.stream()
        .anyMatch(value -> UriMatcher.matchWithNormalizationAndComparison(value, other));
  }

  /**
   * Compares the given URI against all registered URIs and returns the best match result.
   *
   * @param other the requested redirect_uri
   * @return the best UriComparisonResult (SIMPLE_MATCH > NORMALIZED_MATCH > NO_MATCH)
   */
  public UriComparisonResult compareWithDetails(String other) {
    UriComparisonResult bestResult = UriComparisonResult.NO_MATCH;
    for (String value : values) {
      UriComparisonResult result = UriMatcher.compareWithDetails(value, other);
      if (result == UriComparisonResult.SIMPLE_MATCH) {
        return result;
      }
      if (result == UriComparisonResult.NORMALIZED_MATCH) {
        bestResult = result;
      }
    }
    return bestResult;
  }

  /**
   * RFC 8252 Section 7.3: Loopback Interface Redirection
   *
   * <p>For native apps, loopback redirect URIs can use dynamic ports.
   *
   * @param other the requested redirect_uri
   * @return true if matches with loopback port allowance
   */
  public boolean containsWithLoopbackPortAllowance(String other) {
    return values.stream()
        .anyMatch(value -> UriMatcher.matchWithLoopbackPortAllowance(value, other));
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public int size() {
    return values.size();
  }

  public String first() {
    if (isEmpty()) {
      return null;
    }
    return values.get(0);
  }

  @Override
  public Iterator<String> iterator() {
    return values.iterator();
  }
}
