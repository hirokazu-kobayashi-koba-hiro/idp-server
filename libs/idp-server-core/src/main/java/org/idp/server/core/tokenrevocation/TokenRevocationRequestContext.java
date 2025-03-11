package org.idp.server.core.tokenrevocation;

import org.idp.server.core.clientauthenticator.BackchannelRequestContext;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.type.mtls.ClientCert;
import org.idp.server.core.type.oauth.*;

public class TokenRevocationRequestContext implements BackchannelRequestContext {

  ClientSecretBasic clientSecretBasic;
  ClientCert clientCert;
  TokenRevocationRequestParameters parameters;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;

  public TokenRevocationRequestContext(
      ClientSecretBasic clientSecretBasic,
      ClientCert clientCert,
      TokenRevocationRequestParameters parameters,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    this.clientSecretBasic = clientSecretBasic;
    this.clientCert = clientCert;
    this.parameters = parameters;
    this.serverConfiguration = serverConfiguration;
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
  public ServerConfiguration serverConfiguration() {
    return serverConfiguration;
  }

  @Override
  public ClientConfiguration clientConfiguration() {
    return clientConfiguration;
  }

  @Override
  public ClientAuthenticationType clientAuthenticationType() {
    return clientConfiguration.clientAuthenticationType();
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
