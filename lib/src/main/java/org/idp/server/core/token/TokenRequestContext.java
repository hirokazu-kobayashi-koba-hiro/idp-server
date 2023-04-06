package org.idp.server.core.token;

import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.type.*;

public class TokenRequestContext {

  ClientSecretBasic clientSecretBasic;
  TokenRequestParameters parameters;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;

  public TokenRequestContext(
      ClientSecretBasic clientSecretBasic,
      TokenRequestParameters parameters,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    this.clientSecretBasic = clientSecretBasic;
    this.parameters = parameters;
    this.serverConfiguration = serverConfiguration;
    this.clientConfiguration = clientConfiguration;
  }

  public ClientSecretBasic clientSecretBasic() {
    return clientSecretBasic;
  }

  public boolean hasClientSecretBasic() {
    return clientSecretBasic.exists();
  }

  public TokenRequestParameters parameters() {
    return parameters;
  }

  public String getValue(OAuthRequestKey key) {
    return parameters.getString(key);
  }

  public AuthorizationCode code() {
    return parameters.code();
  }

  public boolean hasCode() {
    return parameters().hasCode();
  }

  public GrantType grantType() {
    return parameters.grantType();
  }

  public ServerConfiguration serverConfiguration() {
    return serverConfiguration;
  }

  public ClientConfiguration clientConfiguration() {
    return clientConfiguration;
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
    return clientConfiguration.clientSecret().equals(clientSecret.value());
  }

  public boolean hasClientSecretWithParams() {
    return parameters.hasClientSecret();
  }

  public ClientSecret clientSecretWithParams() {
    return parameters.clientSecret();
  }
}
