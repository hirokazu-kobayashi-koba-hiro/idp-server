package org.idp.server.core.oauth.vp.response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.vc.Credential;

public class VerifiablePresentationBuilder {
  Map<String, Object> values = new HashMap<>();

  public VerifiablePresentationBuilder() {}

  public VerifiablePresentationBuilder addContext(List<String> context) {
    this.values.put("@context", context);
    return this;
  }

  public VerifiablePresentationBuilder addType(List<String> type) {
    this.values.put("type", type);
    return this;
  }

  public VerifiablePresentationBuilder addVerifiableCredential(
      List<Credential> verifiableCredentials) {
    List<Map<String, Object>> credentials =
        verifiableCredentials.stream().map(Credential::values).toList();
    this.values.put("verifiableCredential", credentials);
    return this;
  }

  public VerifiablePresentationBuilder addId(String id) {
    this.values.put("id", id);
    return this;
  }

  public VerifiablePresentationBuilder addHolder(String holder) {
    this.values.put("holder", holder);
    return this;
  }

  public VerifiablePresentationBuilder addProof(Map<String, Object> proof) {
    this.values.put("proof", proof);
    return this;
  }

  public Map<String, Object> build() {
    return values;
  }
}
