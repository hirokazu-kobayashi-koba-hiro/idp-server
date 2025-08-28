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

package org.idp.server.core.openid.oauth.type;

import java.util.Objects;

public class AuthFlow {

  String name;

  public AuthFlow() {}

  public AuthFlow(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public boolean equals(Object object) {
    if (object == null || getClass() != object.getClass()) return false;
    AuthFlow authFlow = (AuthFlow) object;
    return Objects.equals(name, authFlow.name);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name);
  }

  public boolean exists() {
    return name != null && !name.isEmpty();
  }
}
