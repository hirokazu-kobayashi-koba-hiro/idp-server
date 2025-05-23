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

package org.idp.server.basic.type.ciba;

import java.util.Objects;

public enum BackchannelTokenDeliveryMode {
  poll,
  ping,
  push,
  undefined,
  unknown;

  public static BackchannelTokenDeliveryMode of(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      return undefined;
    }
    for (BackchannelTokenDeliveryMode deliveryMode : BackchannelTokenDeliveryMode.values()) {
      if (deliveryMode.name().equals(value)) {
        return deliveryMode;
      }
    }
    return unknown;
  }

  public boolean isDefined() {
    return this == poll || this == ping || this == push;
  }

  public boolean isPushMode() {
    return this == push;
  }

  public boolean isPingMode() {
    return this == ping;
  }
}
