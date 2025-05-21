package org.idp.server.core.oidc.token.tokenrevocation;

import org.idp.server.basic.type.mtls.ClientCert;
import org.idp.server.basic.type.oauth.*;
import org.idp.server.core.oidc.clientauthenticator.BackchannelRequestContext;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;

public class TokenRevocationRequestContext implements BackchannelRequestContext {

  ClientSecretBasic clientSecretBasic;
  ClientCert clientCert;
  TokenRevocationRequestParameters parameters;
  AuthorizationServerConfiguration authorizationServerConfiguration;
  ClientConfiguration clientConfiguration;

  public TokenRevocationRequestContext(
      ClientSecretBasic clientSecretBasic,
      ClientCert clientCert,
      TokenRevocationRequestParameters parameters,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {
    this.clientSecretBasic = clientSecretBasic;
    this.clientCert = clientCert;
    this.parameters = parameters;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
    this.clientConfiguration = clientConfiguration;
  }

  @Override
  public ClientSecretBasic clientSecretBasic() {
    return clientSecretBasic;
  }

  @Override
  public ClientCert clientCert() {
    return clientCert;
  }

  @Override
  public boolean hasClientSecretBasic() {
    return clientSecretBasic.exists();
  }

  public TokenRevocationRequestParameters parameters() {
    return parameters;
  }

  @Override
  public AuthorizationServerConfiguration serverConfiguration() {
    return authorizationServerConfiguration;
  }

  @Override
  public ClientConfiguration clientConfiguration() {
    return clientConfiguration;
  }

  @Override
  public ClientAuthenticationType clientAuthenticationType() {
    return clientConfiguration.clientAuthenticationType();
  }

  public RequestedClientId requestedClientId() {
    if (parameters.hasClientId()) {
      return parameters.clientId();
    }
    return clientSecretBasic.clientId();
  }

  public boolean isSupportedGrantTypeWithServer(GrantType grantType) {
    // FIXME server and client isSupportedGrantType
    return true;
  }

  public boolean isSupportedGrantTypeWithClient(GrantType grantType) {
    // FIXME server and client isSupportedGrantType
    return true;
  }

  public boolean matchClientSecret(ClientSecret clientSecret) {
    return clientConfiguration.clientSecretValue().equals(clientSecret.value());
  }
}
