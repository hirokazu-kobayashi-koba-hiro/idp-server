package org.idp.server.core.authentication.device;

import java.util.Map;
import org.idp.server.core.authentication.AuthenticationTransaction;

public class AuthenticationTransactionFindingResponse {
  int statusCode;
  AuthenticationTransaction authenticationTransaction;

  public static AuthenticationTransactionFindingResponse success(
      AuthenticationTransaction authenticationTransaction) {
    return new AuthenticationTransactionFindingResponse(200, authenticationTransaction);
  }

  public static AuthenticationTransactionFindingResponse notFound() {
    return new AuthenticationTransactionFindingResponse(404, null);
  }

  private AuthenticationTransactionFindingResponse(
      int statusCode, AuthenticationTransaction authenticationTransaction) {
    this.statusCode = statusCode;
    this.authenticationTransaction = authenticationTransaction;
  }

  public int statusCode() {
    return statusCode;
  }

  public AuthenticationTransaction authenticationTransaction() {
    return authenticationTransaction;
  }

  public Map<String, Object> contents() {
    if (statusCode == 200) {
      return authenticationTransaction.toMap();
    }
    return Map.of();
  }
}
