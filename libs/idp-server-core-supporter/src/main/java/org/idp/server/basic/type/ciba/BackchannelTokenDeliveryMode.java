package org.idp.server.basic.type.ciba;

import java.util.Objects;

public enum BackchannelTokenDeliveryMode {
  poll, ping, push, undefined, unknown;

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
