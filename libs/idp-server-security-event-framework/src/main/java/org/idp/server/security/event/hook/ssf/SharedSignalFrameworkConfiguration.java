package org.idp.server.security.event.hook.ssf;

import java.util.Map;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.platform.security.event.SecurityEventType;

public class SharedSignalFrameworkConfiguration implements JsonReadable {

  String issuer;
  SharedSignalFrameworkConfig base;
  Map<String, SharedSignalFrameworkConfig> overlays;

  public SharedSignalFrameworkConfiguration() {}

  public SharedSignalFrameworkConfiguration(
      String issuer,
      SharedSignalFrameworkConfig base,
      Map<String, SharedSignalFrameworkConfig> overlays) {
    this.issuer = issuer;
    this.base = base;
    this.overlays = overlays;
  }

  public String issuer() {
    return issuer;
  }

  public String privateKey(SecurityEventType type) {
    if (overlays.containsKey(type.value())) {
      return overlays.get(type.value()).privateKey();
    }
    return base.privateKey();
  }

  public String endpoint(SecurityEventType type) {
    if (overlays.containsKey(type.value())) {
      return overlays.get(type.value()).endpoint();
    }
    return base.endpoint();
  }

  public Map<String, String> headers(SecurityEventType type) {
    if (overlays.containsKey(type.value())) {
      return overlays.get(type.value()).headers();
    }
    return base.headers();
  }
}
