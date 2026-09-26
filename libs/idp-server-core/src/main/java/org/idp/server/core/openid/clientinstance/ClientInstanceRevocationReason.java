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
 * Why a Client Instance was revoked. Kept on the instance, since the security event that also
 * records it is retained for a limited time only.
 */
public enum ClientInstanceRevocationReason {
  /** Revoked through the management API: a lost device, a compromised key. */
  operator,
  /** The same user registered a newer instance of the client, which took its place. */
  superseded,
  undefined;

  public static ClientInstanceRevocationReason of(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      return undefined;
    }
    for (ClientInstanceRevocationReason reason : values()) {
      if (reason.name().equals(value)) {
        return reason;
      }
    }
    return undefined;
  }

  public boolean exists() {
    return this != undefined;
  }
}
