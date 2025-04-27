package org.idp.server.core.identity.verification.trustframework;

import java.util.Map;
import org.idp.server.core.basic.json.JsonNodeWrapper;

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
}
