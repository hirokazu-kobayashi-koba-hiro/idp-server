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


package org.idp.server.platform.security.hook;

import org.idp.server.platform.uuid.UuidConvertable;

import java.util.Objects;
import java.util.UUID;

public class SecurityEventHookConfigurationIdentifier implements UuidConvertable {

  String value;

  public SecurityEventHookConfigurationIdentifier() {}

  public SecurityEventHookConfigurationIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public UUID valueAsUuid() {
    return convertUuid(value);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    SecurityEventHookConfigurationIdentifier that = (SecurityEventHookConfigurationIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }

  public boolean exists() {
    return value != null && !value.isEmpty();
  }
}
