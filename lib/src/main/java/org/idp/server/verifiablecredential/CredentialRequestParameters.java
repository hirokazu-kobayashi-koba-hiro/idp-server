package org.idp.server.verifiablecredential;

import java.util.Map;
import org.idp.server.type.OAuthRequestKey;
import org.idp.server.type.verifiablecredential.Format;
import org.idp.server.type.verifiablecredential.ProofEntity;

public class CredentialRequestParameters {
  Map<String, Object> values;

  public CredentialRequestParameters() {
    this.values = Map.of();
  }

  public CredentialRequestParameters(Map<String, Object> values) {
    this.values = values;
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public Format format() {
    return Format.of(getStringOrEmpty(OAuthRequestKey.format));
  }

  public ProofEntity proofEntity() {
    return new ProofEntity(values.get(OAuthRequestKey.proof.name()));
  }

  public String getStringOrEmpty(OAuthRequestKey key) {
    return (String) values.getOrDefault(key.name(), "");
  }

  public boolean contains(OAuthRequestKey key) {
    return values.containsKey(key.name());
  }

  public boolean hasFormat() {
    return contains(OAuthRequestKey.format);
  }
}
