package org.idp.server.handler.credential.io;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.verifiablecredential.VerifiableCredentialErrorResponse;
import org.idp.server.verifiablecredential.VerifiableCredentialResponse;

public class CredentialResponse {
  CredentialRequestStatus status;
  VerifiableCredentialResponse response;
  VerifiableCredentialErrorResponse errorResponse;
  Map<String, String> headers;

  public CredentialResponse(CredentialRequestStatus status, VerifiableCredentialResponse response) {
    this.status = status;
    this.response = response;
    this.errorResponse = new VerifiableCredentialErrorResponse();
    Map<String, String> values = new HashMap<>();
    values.put("Content-Typ", "application/json");
    values.put("Cache-Control", "no-store");
    values.put("Pragma", "no-cache");
    this.headers = values;
  }

  public CredentialResponse(
      CredentialRequestStatus status, VerifiableCredentialErrorResponse errorResponse) {
    this.status = status;
    this.response = new VerifiableCredentialResponse();
    this.errorResponse = errorResponse;
    Map<String, String> values = new HashMap<>();
    values.put("Content-Typ", "application/json");
    values.put("Cache-Control", "no-store");
    values.put("Pragma", "no-cache");
    this.headers = values;
  }

  public String contents() {
    if (status.isOK()) {
      return response.contents();
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
