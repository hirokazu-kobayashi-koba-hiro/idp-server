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

package org.idp.server.core.oidc.grant.consent;

import java.time.LocalDateTime;
import java.util.Objects;
import org.idp.server.basic.json.JsonReadable;

public class ConsentClaim implements JsonReadable {
  String name;
  String value;
  LocalDateTime consentedAt;

  public ConsentClaim() {}

  public ConsentClaim(String name, String value, LocalDateTime consentedAt) {
    this.name = name;
    this.value = value;
    this.consentedAt = consentedAt;
  }

  public String name() {
    return name;
  }

  public String value() {
    return value;
  }

  public LocalDateTime consentedAt() {
    return consentedAt;
  }

  public boolean isConsented(ConsentClaim requested) {
    return name.equals(requested.name()) && value.equals(requested.value());
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    ConsentClaim that = (ConsentClaim) o;
    return Objects.equals(name, that.name) && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, value);
  }
}
