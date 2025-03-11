package org.idp.server.core.oauth.vp.response;

import java.util.Map;

public class VerifiablePresentation {
  Map<String, Object> values;

  public VerifiablePresentation() {
    this.values = Map.of();
  }

  public VerifiablePresentation(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> values() {
    return values;
  }
}
