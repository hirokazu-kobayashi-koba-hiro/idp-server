package org.idp.server.core.verifiablecredential.request;

import java.util.Map;
import org.idp.server.core.type.OAuthRequestKey;
import org.idp.server.core.type.verifiablecredential.CredentialDefinitionEntity;
import org.idp.server.core.type.verifiablecredential.Format;
import org.idp.server.core.type.verifiablecredential.ProofEntity;

public class CredentialRequestParameters implements VerifiableCredentialRequestTransformable {
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
    return Format.of(getValueOrEmpty(OAuthRequestKey.format));
  }

  public CredentialDefinitionEntity credentialDefinitionEntity() {
    return new CredentialDefinitionEntity(values.get(OAuthRequestKey.credential_definition.name()));
  }

  public ProofEntity proofEntity() {
    return new ProofEntity(values.get(OAuthRequestKey.proof.name()));
  }

  public String getValueOrEmpty(OAuthRequestKey key) {
    return (String) values.getOrDefault(key.name(), "");
  }

  public boolean contains(OAuthRequestKey key) {
    return values.containsKey(key.name());
  }

  public boolean isDefined() {
    return contains(OAuthRequestKey.format) && format().isDefined();
  }

  public boolean hasProof() {
    return contains(OAuthRequestKey.proof);
  }

  public Map<String, Object> values() {
    return values;
  }
}
