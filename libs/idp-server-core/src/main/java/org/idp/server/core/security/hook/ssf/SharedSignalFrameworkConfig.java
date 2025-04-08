package org.idp.server.core.security.hook.ssf;

import org.idp.server.core.basic.json.JsonReadable;

import java.util.Map;
import java.util.Objects;

public class SharedSignalFrameworkConfig implements JsonReadable {

  String privateKey;
  String endpoint;
  Map<String, String> headers;

  public SharedSignalFrameworkConfig() {}

  public SharedSignalFrameworkConfig(
      String privateKey,
      String endpoint,
      Map<String, String> headers) {
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
