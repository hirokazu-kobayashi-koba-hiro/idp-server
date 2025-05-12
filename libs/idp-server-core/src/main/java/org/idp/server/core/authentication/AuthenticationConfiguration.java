package org.idp.server.core.authentication;

import java.util.Map;

public class AuthenticationConfiguration {
  String id;
  String type;
  Map<String, Object> payload;

  public AuthenticationConfiguration() {}

  public AuthenticationConfiguration(String id, String type, Map<String, Object> payload) {
    this.id = id;
    this.type = type;
    this.payload = payload;
  }

  public String id() {
    return id;
  }

  public AuthenticationConfigurationIdentifier identifier() {
    return new AuthenticationConfigurationIdentifier(id);
  }

  public String type() {
    return type;
  }

  public Map<String, Object> payload() {
    return payload;
  }
}
