/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.identity.verification.trustframework;

import java.util.Map;
import org.idp.server.basic.json.JsonNodeWrapper;

public class TrustFrameworkDetails {

  JsonNodeWrapper json;

  public TrustFrameworkDetails() {
    this.json = JsonNodeWrapper.empty();
  }

  public TrustFrameworkDetails(JsonNodeWrapper json) {
    this.json = json;
  }

  public Map<String, Object> toMap() {
    return json.toMap();
  }

  public boolean exists() {
    return json != null && json.exists();
  }
}
