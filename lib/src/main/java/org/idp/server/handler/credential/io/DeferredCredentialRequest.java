package org.idp.server.handler.credential.io;

import java.util.Map;
import org.idp.server.token.AuthorizationHeaderHandlerable;
import org.idp.server.type.mtls.ClientCert;
import org.idp.server.type.oauth.AccessTokenEntity;
import org.idp.server.type.oauth.TokenIssuer;
import org.idp.server.type.verifiablecredential.TransactionId;
import org.idp.server.verifiablecredential.request.DeferredCredentialRequestParameters;

public class DeferredCredentialRequest implements AuthorizationHeaderHandlerable {
  String authorizationHeaders;
  Map<String, Object> params;
  String issuer;
  String clientCert;

  public DeferredCredentialRequest(
      String authorizationHeaders, Map<String, Object> params, String issuer) {
    this.authorizationHeaders = authorizationHeaders;
    this.params = params;
    this.issuer = issuer;
  }

  public DeferredCredentialRequest setClientCert(String clientCert) {
    this.clientCert = clientCert;
    return this;
  }

  public String getAuthorizationHeaders() {
    return authorizationHeaders;
  }

  public Map<String, Object> getParams() {
    return params;
  }

  public String getIssuer() {
    return issuer;
  }

  public String getClientCert() {
    return clientCert;
  }

  public AccessTokenEntity toAccessToken() {
    return extractAccessToken(authorizationHeaders);
  }

  public DeferredCredentialRequestParameters toParameters() {
    return new DeferredCredentialRequestParameters(params);
  }

  public TransactionId transactionId() {
    return toParameters().transactionId();
  }

  public TokenIssuer toTokenIssuer() {
    return new TokenIssuer(issuer);
  }

  public ClientCert toClientCert() {
    return new ClientCert(clientCert);
  }
}
