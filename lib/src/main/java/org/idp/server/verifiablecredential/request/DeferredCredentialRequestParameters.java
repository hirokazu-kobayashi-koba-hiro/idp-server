package org.idp.server.verifiablecredential.request;

import java.util.Map;
import org.idp.server.type.OAuthRequestKey;
import org.idp.server.type.verifiablecredential.Format;
import org.idp.server.type.verifiablecredential.ProofEntity;
import org.idp.server.type.verifiablecredential.TransactionId;

public class DeferredCredentialRequestParameters
    implements VerifiableCredentialRequestTransformable {
  Map<String, Object> values;

  public DeferredCredentialRequestParameters() {
    this.values = Map.of();
  }

  public DeferredCredentialRequestParameters(Map<String, Object> values) {
    this.values = values;
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public Format format() {
    return Format.of(getValueOrEmpty(OAuthRequestKey.format));
  }

  public TransactionId transactionId() {
    return new TransactionId((String) getValueOrEmpty(OAuthRequestKey.transaction_id));
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
