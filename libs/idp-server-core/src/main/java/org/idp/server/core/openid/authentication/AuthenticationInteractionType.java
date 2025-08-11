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

package org.idp.server.core.openid.authentication;

import java.util.Objects;

public class AuthenticationInteractionType {
  String name;

  public AuthenticationInteractionType() {}

  public AuthenticationInteractionType(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    AuthenticationInteractionType that = (AuthenticationInteractionType) o;
    return Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name);
  }

  public String formatSnakeCase() {
    String[] parts = name.split("-");
    StringBuilder result = new StringBuilder();
    for (String part : parts) {
      if (!result.isEmpty()) {
        result.append("_");
      }
      result.append(part);
    }
    return result.toString();
  }
}
