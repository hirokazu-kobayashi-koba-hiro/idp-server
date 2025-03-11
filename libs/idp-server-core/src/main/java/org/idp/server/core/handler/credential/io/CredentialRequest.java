package org.idp.server.core.handler.credential.io;

import java.util.Map;
import org.idp.server.core.token.AuthorizationHeaderHandlerable;
import org.idp.server.core.type.mtls.ClientCert;
import org.idp.server.core.type.oauth.AccessTokenEntity;
import org.idp.server.core.type.oauth.TokenIssuer;
import org.idp.server.core.verifiablecredential.request.CredentialRequestParameters;

public class CredentialRequest implements AuthorizationHeaderHandlerable {
  String authorizationHeaders;
  Map<String, Object> params;
  String issuer;
  String clientCert;

  public CredentialRequest(String authorizationHeaders, Map<String, Object> params, String issuer) {
    this.authorizationHeaders = authorizationHeaders;
    this.params = params;
    this.issuer = issuer;
  }

  public CredentialRequest setClientCert(String clientCert) {
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

  public CredentialRequestParameters toParameters() {
    return new CredentialRequestParameters(params);
  }

  public TokenIssuer toTokenIssuer() {
    return new TokenIssuer(issuer);
  }

  public ClientCert toClientCert() {
    return new ClientCert(clientCert);
  }
}
