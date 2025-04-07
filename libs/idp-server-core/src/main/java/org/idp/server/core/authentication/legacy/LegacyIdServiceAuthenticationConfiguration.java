package org.idp.server.core.authentication.legacy;

import java.util.Map;
import org.idp.server.core.basic.json.JsonReadable;

public class LegacyIdServiceAuthenticationConfiguration implements JsonReadable {
  String type;
  String providerName;
  Map<String, LegacyIdServiceAuthenticationDetailConfiguration> details;

  public LegacyIdServiceAuthenticationConfiguration() {}

  public String getType() {
    return type;
  }

  public LegacyIdServiceAuthenticationDetailConfiguration authenticationDetailConfig() {
    return details.get("authentication");
  }

  public LegacyIdServiceAuthenticationDetailConfiguration userinfoDetailConfig() {
    return details.get("userinfo");
  }

  public String providerName() {
    return providerName;
  }
}
