package org.idp.server.core.authentication.legacy;

import java.util.Map;
import org.idp.server.core.basic.http.*;
import org.idp.server.core.basic.json.JsonReadable;

public class LegacyIdServiceAuthenticationConfiguration implements JsonReadable {
  String type;
  String providerName;
  Map<String, LegacyIdServiceAuthenticationDetailConfiguration> details;

  public LegacyIdServiceAuthenticationConfiguration() {}

  public LegacyIdServiceAuthenticationDetailConfiguration getAuthenticationDetailConfig() {
    return details.get("authentication");
  }

  public LegacyIdServiceAuthenticationDetailConfiguration getUserinfoDetailConfig() {
    return details.get("userinfo");
  }

  public String providerName() {
    return providerName;
  }
}
