package org.idp.server.core.token;

import java.util.Objects;
import org.idp.server.core.clientauthenticator.BackchannelRequestContext;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.type.OAuthRequestKey;
import org.idp.server.core.type.ciba.AuthReqId;
import org.idp.server.core.type.ciba.BackchannelTokenDeliveryMode;
import org.idp.server.core.type.extension.CustomProperties;
import org.idp.server.core.type.mtls.ClientCert;
import org.idp.server.core.type.oauth.*;
import org.idp.server.core.type.pkce.CodeVerifier;

public class TokenRequestContext implements BackchannelRequestContext {

  ClientSecretBasic clientSecretBasic;
  ClientCert clientCert;
  TokenRequestParameters parameters;
  CustomProperties customProperties;
  PasswordCredentialsGrantDelegate passwordCredentialsGrantDelegate;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;

  public TokenRequestContext(
      ClientSecretBasic clientSecretBasic,
      ClientCert clientCert,
      TokenRequestParameters parameters,
      CustomProperties customProperties,
      PasswordCredentialsGrantDelegate passwordCredentialsGrantDelegate,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    this.clientSecretBasic = clientSecretBasic;
    this.clientCert = clientCert;
    this.parameters = parameters;
    this.customProperties = customProperties;
    this.passwordCredentialsGrantDelegate = passwordCredentialsGrantDelegate;
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

  public TokenRequestParameters parameters() {
    return parameters;
  }

  public String getValue(OAuthRequestKey key) {
    return parameters.getValueOrEmpty(key);
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

  public CustomProperties customProperties() {
    return customProperties;
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
    return serverConfiguration.isSupportedGrantType(grantType);
  }

  public boolean isSupportedGrantTypeWithClient(GrantType grantType) {
    return clientConfiguration.isSupportedGrantType(grantType);
  }

  public boolean matchClientSecret(ClientSecret clientSecret) {
    return clientConfiguration.clientSecretValue().equals(clientSecret.value());
  }

  public boolean hasClientSecretWithParams() {
    return parameters.hasClientSecret();
  }

  public ClientSecret clientSecretWithParams() {
    return parameters.clientSecret();
  }

  public TokenIssuer tokenIssuer() {
    return serverConfiguration.tokenIssuer();
  }

  public boolean hasRefreshToken() {
    return parameters.hasRefreshToken();
  }

  public RefreshTokenEntity refreshToken() {
    return parameters.refreshToken();
  }

  public Scopes scopes() {
    return parameters.scopes();
  }

  public boolean hasClientId() {
    return parameters.hasClientId();
  }

  public RedirectUri redirectUri() {
    return parameters.redirectUri();
  }

  public boolean hasCodeVerifier() {
    return parameters.hasCodeVerifier();
  }

  public CodeVerifier codeVerifier() {
    return parameters.codeVerifier();
  }

  public boolean hasAuthReqId() {
    return parameters.hasAuthReqId();
  }

  public AuthReqId authReqId() {
    return parameters.authReqId();
  }

  public ClientId clientId() {
    return clientConfiguration.clientId();
  }

  public BackchannelTokenDeliveryMode deliveryMode() {
    return clientConfiguration.backchannelTokenDeliveryMode();
  }

  public boolean isPushMode() {
    return deliveryMode().isPushMode();
  }

  public PasswordCredentialsGrantDelegate passwordCredentialsGrantDelegate() {
    return passwordCredentialsGrantDelegate;
  }

  public boolean isSupportedPasswordGrant() {
    return Objects.nonNull(passwordCredentialsGrantDelegate);
  }

  public Username username() {
    return parameters.username();
  }

  public boolean hasUsername() {
    return parameters.hasUsername();
  }

  public Password password() {
    return parameters.password();
  }

  public boolean hasPassword() {
    return parameters.hasPassword();
  }
}
