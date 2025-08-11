/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.openid.token;

import java.util.Objects;
import org.idp.server.core.openid.oauth.clientauthenticator.BackchannelRequestContext;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientIdentifier;
import org.idp.server.core.openid.oauth.type.OAuthRequestKey;
import org.idp.server.core.openid.oauth.type.ciba.AuthReqId;
import org.idp.server.core.openid.oauth.type.ciba.BackchannelTokenDeliveryMode;
import org.idp.server.core.openid.oauth.type.extension.CustomProperties;
import org.idp.server.core.openid.oauth.type.mtls.ClientCert;
import org.idp.server.core.openid.oauth.type.oauth.*;
import org.idp.server.core.openid.oauth.type.pkce.CodeVerifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class TokenRequestContext implements BackchannelRequestContext {

  Tenant tenant;
  ClientSecretBasic clientSecretBasic;
  ClientCert clientCert;
  TokenRequestParameters parameters;
  CustomProperties customProperties;
  PasswordCredentialsGrantDelegate passwordCredentialsGrantDelegate;
  AuthorizationServerConfiguration authorizationServerConfiguration;
  ClientConfiguration clientConfiguration;

  public TokenRequestContext(
      Tenant tenant,
      ClientSecretBasic clientSecretBasic,
      ClientCert clientCert,
      TokenRequestParameters parameters,
      CustomProperties customProperties,
      PasswordCredentialsGrantDelegate passwordCredentialsGrantDelegate,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {
    this.tenant = tenant;
    this.clientSecretBasic = clientSecretBasic;
    this.clientCert = clientCert;
    this.parameters = parameters;
    this.customProperties = customProperties;
    this.passwordCredentialsGrantDelegate = passwordCredentialsGrantDelegate;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
    this.clientConfiguration = clientConfiguration;
  }

  public Tenant tenant() {
    return tenant;
  }

  public TenantIdentifier tenantIdentifier() {
    return tenant.identifier();
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

  public boolean isSupportedGrantTypeWithServer(GrantType grantType) {
    return authorizationServerConfiguration.isSupportedGrantType(grantType);
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
    return authorizationServerConfiguration.tokenIssuer();
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

  public RequestedClientId requestedClientId() {
    if (parameters.hasClientId()) {
      return parameters.clientId();
    }
    return clientSecretBasic.clientId();
  }

  public ClientIdentifier clientIdentifier() {
    return clientConfiguration.clientIdentifier();
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
