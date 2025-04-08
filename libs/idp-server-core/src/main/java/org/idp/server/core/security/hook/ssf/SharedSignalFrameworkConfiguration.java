package org.idp.server.core.security.hook.ssf;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.basic.json.JsonReadable;

public class SharedSignalFrameworkConfiguration implements JsonReadable {

  String issuer;
  List<String> targetEvents;
  String privateKey;
  String endpoint;
  Map<String, String> headers;

  public SharedSignalFrameworkConfiguration() {}

  public SharedSignalFrameworkConfiguration(
      String issuer,
      List<String> targetEvents,
      String privateKey,
      String endpoint,
      Map<String, String> headers) {
    this.issuer = issuer;
    this.targetEvents = targetEvents;
    this.privateKey = privateKey;
    this.endpoint = endpoint;
    this.headers = headers;
  }

  public String issuer() {
    return issuer;
  }

  public List<String> targetEvents() {
    return targetEvents;
  }

  public String privateKey() {
    return privateKey;
  }

  public String endpoint() {
    return endpoint;
  }

  public Map<String, String> headers() {
    return headers;
  }

  public boolean containsEvent(String event) {
    return targetEvents.contains(event);
  }

  public boolean exists() {
    return Objects.nonNull(issuer) && !issuer.isEmpty();
  }
}
