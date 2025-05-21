package org.idp.server.security.event.hook.ssf;

import java.util.Map;
import org.idp.server.basic.json.JsonReadable;

public class SharedSignalFrameworkConfig implements JsonReadable {

  String privateKey;
  String endpoint;
  Map<String, String> headers;

  public SharedSignalFrameworkConfig() {}

  public SharedSignalFrameworkConfig(
      String privateKey, String endpoint, Map<String, String> headers) {
    this.privateKey = privateKey;
    this.endpoint = endpoint;
    this.headers = headers;
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
}
