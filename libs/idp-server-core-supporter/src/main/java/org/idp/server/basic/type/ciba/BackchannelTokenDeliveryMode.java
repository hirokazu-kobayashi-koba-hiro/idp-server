/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
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
