package org.idp.server.core.type.mtls;

import java.util.Objects;
import org.idp.server.core.basic.base64.Base64Codeable;

public class ClientCert implements Base64Codeable {
  String value;

  public ClientCert() {}

  public ClientCert(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public String plainValue() {
    if (value.contains("-----BEGIN CERTIFICATE-----")) {
      return value.replaceAll("%0A", "\n");
    }
    return decodeWithUrlSafe(value);
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }
}
