package org.idp.server.core.handler.credential.io;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.verifiablecredential.BatchVerifiableCredentialResponses;
import org.idp.server.core.verifiablecredential.VerifiableCredentialErrorResponse;

public class BatchCredentialResponse {
  CredentialRequestStatus status;
  BatchVerifiableCredentialResponses responses;
  VerifiableCredentialErrorResponse errorResponse;
  Map<String, String> headers;

  public BatchCredentialResponse(
      CredentialRequestStatus status, BatchVerifiableCredentialResponses responses) {
    this.status = status;
    this.responses = responses;
    this.errorResponse = new VerifiableCredentialErrorResponse();
    Map<String, String> values = new HashMap<>();
    values.put("Content-Typ", "application/json");
    values.put("Cache-Control", "no-store");
    values.put("Pragma", "no-cache");
    this.headers = values;
  }

  public BatchCredentialResponse(
      CredentialRequestStatus status, VerifiableCredentialErrorResponse errorResponse) {
    this.status = status;
    this.responses = new BatchVerifiableCredentialResponses();
    this.errorResponse = errorResponse;
    Map<String, String> values = new HashMap<>();
    values.put("Content-Typ", "application/json");
    values.put("Cache-Control", "no-store");
    values.put("Pragma", "no-cache");
    this.headers = values;
  }

  public String contents() {
    if (status.isOK()) {
      return responses.contents();
    }
    return errorResponse.contents();
  }

  public int statusCode() {
    return status.statusCode();
  }

  public Map<String, String> headers() {
    return headers;
  }
}
