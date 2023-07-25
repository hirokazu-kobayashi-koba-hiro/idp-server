package org.idp.server.verifiablecredential.request;

import java.util.Map;
import org.idp.server.verifiablecredential.exception.VerifiableCredentialRequestInvalidException;

public class BatchCredentialRequestParameters implements VerifiableCredentialRequestTransformable {
  Map<String, Object> values;

  public BatchCredentialRequestParameters() {
    this.values = Map.of();
  }

  public BatchCredentialRequestParameters(Map<String, Object> values) {
    this.values = values;
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public Object credentialRequests() {
    return values.get("credential_requests");
  }

  public boolean hasCredentialRequests() {
    return values.containsKey("credential_requests");
  }

  // FIXME
  public BatchCredentialRequests toBatchCredentialRequest() {
    try {
      return transformBatchRequest(credentialRequests());
    } catch (VerifiableCredentialRequestInvalidException e) {
      throw new RuntimeException(e);
    }
  }
}
