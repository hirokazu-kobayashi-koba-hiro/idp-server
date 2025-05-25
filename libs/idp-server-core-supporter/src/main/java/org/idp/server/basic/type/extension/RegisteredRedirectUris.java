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

package org.idp.server.basic.type.extension;

import java.util.Iterator;
import java.util.List;
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

  @Override
  public Iterator<String> iterator() {
    return values.iterator();
  }
}
