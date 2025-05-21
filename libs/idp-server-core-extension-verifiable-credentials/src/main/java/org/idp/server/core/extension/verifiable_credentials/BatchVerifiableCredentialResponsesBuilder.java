package org.idp.server.core.extension.verifiable_credentials;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.type.verifiablecredential.CNonce;
import org.idp.server.basic.type.verifiablecredential.CNonceExpiresIn;

public class BatchVerifiableCredentialResponsesBuilder {
  List<BatchVerifiableCredentialResponse> responses;
  CNonce cNonce;
  CNonceExpiresIn cNonceExpiresIn;
  Map<String, Object> values = new HashMap<>();
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  public BatchVerifiableCredentialResponsesBuilder() {}

  public BatchVerifiableCredentialResponsesBuilder add(
      List<BatchVerifiableCredentialResponse> responses) {
    this.responses = responses;
    values.put("credential_responses", toResponses(responses));
    return this;
  }

  private List<Map<String, Object>> toResponses(List<BatchVerifiableCredentialResponse> responses) {
    return responses.stream().map(BatchVerifiableCredentialResponse::toMap).toList();
  }

  public BatchVerifiableCredentialResponsesBuilder add(CNonce cNonce) {
    this.cNonce = cNonce;
    values.put("c_nonce", cNonce.value());
    return this;
  }

  public BatchVerifiableCredentialResponsesBuilder add(CNonceExpiresIn cNonceExpiresIn) {
    this.cNonceExpiresIn = cNonceExpiresIn;
    values.put("c_nonce_expires_in", cNonceExpiresIn.value());
    return this;
  }

  public BatchVerifiableCredentialResponses build() {
    String contents = jsonConverter.write(values);
    return new BatchVerifiableCredentialResponses(responses, cNonce, cNonceExpiresIn, contents);
  }
}
