/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authentication.interactors.legacy;

import java.util.Map;
import org.idp.server.basic.json.JsonReadable;

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
