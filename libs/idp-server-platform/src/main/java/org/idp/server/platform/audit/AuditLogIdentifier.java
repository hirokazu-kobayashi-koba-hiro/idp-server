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

package org.idp.server.platform.audit;

import java.util.Objects;
import java.util.UUID;
import org.idp.server.platform.uuid.UuidConvertable;

public class AuditLogIdentifier implements UuidConvertable {
  String value;

  public AuditLogIdentifier() {}

  public AuditLogIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public UUID valueAsUuid() {
    return convertUuid(value);
  }

  @Override
  public boolean equals(Object object) {
    if (object == null || getClass() != object.getClass()) return false;
    AuditLogIdentifier that = (AuditLogIdentifier) object;
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
