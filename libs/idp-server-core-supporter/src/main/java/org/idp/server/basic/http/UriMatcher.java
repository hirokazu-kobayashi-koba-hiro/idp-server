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

package org.idp.server.basic.http;

import java.net.URI;

public class UriMatcher {

  public static boolean matchWithNormalizationAndComparison(String target, String other) {
    if (equalsSimpleComparison(target, other)) {
      return true;
    }
    return matchSyntaxBasedNormalization(target, other);
  }

  static boolean equalsSimpleComparison(String target, String other) {
    return target.equals(other);
  }

  static boolean matchSyntaxBasedNormalization(String target, String other) {
    try {
      UriWrapper targetUri = new UriWrapper(new URI(target));
      UriWrapper otherUri = new UriWrapper(new URI(other));
      if (!targetUri.equalsUserinfo(otherUri)) {
        return false;
      }
      if (!targetUri.equalsPath(otherUri)) {
        return false;
      }
      if (!targetUri.equalsHost(otherUri)) {
        return false;
      }
      return targetUri.equalsPort(otherUri);
    } catch (Exception e) {
      return false;
    }
  }
}
