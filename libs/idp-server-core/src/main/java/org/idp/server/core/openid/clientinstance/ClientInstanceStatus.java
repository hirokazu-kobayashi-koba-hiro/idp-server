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

package org.idp.server.core.openid.clientinstance;

import java.util.Objects;

/**
 * Lifecycle status of a Client Instance.
 *
 * <p>An unset or unknown value resolves to {@link #undefined}, which is not usable for client
 * authentication: an instance is trusted only when it is explicitly {@link #active}.
 */
public enum ClientInstanceStatus {
  active,
  revoked,
  undefined;

  public static ClientInstanceStatus of(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      return undefined;
    }
    for (ClientInstanceStatus status : ClientInstanceStatus.values()) {
      if (status.name().equals(value)) {
        return status;
      }
    }
    return undefined;
  }

  public boolean isActive() {
    return this == active;
  }

  public boolean isRevoked() {
    return this == revoked;
  }

  public boolean isUndefined() {
    return this == undefined;
  }
}
