package org.idp.server.core.verifiablecredential.request;

import java.util.Map;
import org.idp.server.core.type.OAuthRequestKey;
import org.idp.server.core.type.verifiablecredential.Format;
import org.idp.server.core.type.verifiablecredential.TransactionId;

public class DeferredCredentialRequestParameters {
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
    return new TransactionId(getValueOrEmpty(OAuthRequestKey.transaction_id));
  }

  public boolean hasTransactionId() {
    return contains(OAuthRequestKey.transaction_id);
  }

  public String getValueOrEmpty(OAuthRequestKey key) {
    return (String) values.getOrDefault(key.name(), "");
  }

  public boolean contains(OAuthRequestKey key) {
    return values.containsKey(key.name());
  }
}
