package org.idp.server.core.federation;

import java.util.Map;
import org.idp.server.core.federation.sso.SsoProvider;

public class FederationConfiguration {
  String id;
  String type;
  String ssoProvider;
  Map<String, Object> payload;

  public FederationConfiguration() {}

  public FederationConfiguration(String id, String type, Map<String, Object> payload) {
    this.id = id;
    this.type = type;
    this.payload = payload;
  }

  public FederationConfigurationIdentifier identifier() {
    return new FederationConfigurationIdentifier(id);
  }

  public FederationType type() {
    return new FederationType(type);
  }

  public SsoProvider ssoProvider() {
    return new SsoProvider(ssoProvider);
  }

  public Map<String, Object> payload() {
    return payload;
  }

  public boolean exists() {
    return id != null && !id.isEmpty();
  }
}
