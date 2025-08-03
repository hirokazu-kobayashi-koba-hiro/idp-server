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

package org.idp.server.core.extension.ciba;

import java.util.HashMap;
import java.util.List;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.extension.ciba.user.UserHint;
import org.idp.server.core.extension.ciba.user.UserHintRelatedParams;
import org.idp.server.core.extension.ciba.user.UserHintType;
import org.idp.server.core.oidc.authentication.AuthenticationInteractionType;
import org.idp.server.core.oidc.clientauthenticator.BackchannelRequestContext;
import org.idp.server.core.oidc.clientauthenticator.BackchannelRequestParameters;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.authentication.AuthenticationPolicy;
import org.idp.server.core.oidc.configuration.client.ClientAttributes;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.rar.AuthorizationDetails;
import org.idp.server.core.oidc.type.ciba.Interval;
import org.idp.server.core.oidc.type.ciba.UserCode;
import org.idp.server.core.oidc.type.mtls.ClientCert;
import org.idp.server.core.oidc.type.oauth.*;
import org.idp.server.core.oidc.type.oidc.AcrValues;
import org.idp.server.platform.jose.JoseContext;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class CibaRequestContext implements BackchannelRequestContext {

  Tenant tenant;
  CibaRequestPattern pattern;
  ClientSecretBasic clientSecretBasic;
  ClientCert clientCert;
  CibaRequestParameters parameters;
  CibaRequestObjectParameters requestObjectParameters;
  CibaRequestAssembleParameters assembleParameters;
  JoseContext joseContext;
  BackchannelAuthenticationRequest backchannelAuthenticationRequest;
  AuthorizationServerConfiguration authorizationServerConfiguration;
  ClientConfiguration clientConfiguration;

  public CibaRequestContext() {}

  public CibaRequestContext(
      Tenant tenant,
      CibaRequestPattern pattern,
      ClientSecretBasic clientSecretBasic,
      ClientCert clientCert,
      CibaRequestParameters parameters,
      CibaRequestObjectParameters requestObjectParameters,
      JoseContext joseContext,
      BackchannelAuthenticationRequest backchannelAuthenticationRequest,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {
    this.tenant = tenant;
    this.pattern = pattern;
    this.clientSecretBasic = clientSecretBasic;
    this.clientCert = clientCert;
    this.parameters = parameters;
    this.requestObjectParameters = requestObjectParameters;
    this.assembleParameters =
        new CibaRequestAssembleParameters(parameters, requestObjectParameters);
    this.joseContext = joseContext;
    this.backchannelAuthenticationRequest = backchannelAuthenticationRequest;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
    this.clientConfiguration = clientConfiguration;
  }

  public Tenant tenant() {
    return tenant;
  }

  public CibaRequestPattern pattern() {
    return pattern;
  }

  public TokenIssuer tokenIssuer() {
    return authorizationServerConfiguration.tokenIssuer();
  }

  @Override
  public BackchannelRequestParameters parameters() {
    return assembleParameters;
  }

  public CibaRequestParameters cibaRequestParameters() {
    return parameters;
  }

  public CibaRequestObjectParameters cibaRequestObjectParameters() {
    return requestObjectParameters;
  }

  public JoseContext joseContext() {
    return joseContext;
  }

  public BackchannelAuthenticationRequest backchannelAuthenticationRequest() {
    return backchannelAuthenticationRequest;
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

  public BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier() {
    return backchannelAuthenticationRequest.identifier();
  }

  public Interval interval() {
    return new Interval(
        authorizationServerConfiguration.backchannelAuthenticationPollingInterval());
  }

  public ExpiresIn expiresIn() {
    if (backchannelAuthenticationRequest.hasRequestedExpiry()) {
      return new ExpiresIn(backchannelAuthenticationRequest.requestedExpiry().toIntValue());
    }
    return new ExpiresIn(
        authorizationServerConfiguration.backchannelAuthenticationRequestExpiresIn());
  }

  public boolean hasUserCode() {
    return backchannelAuthenticationRequest.hasUserCode();
  }

  public UserCode userCode() {
    return backchannelAuthenticationRequest.userCode();
  }

  public RequestedClientId requestedClientId() {
    return backchannelAuthenticationRequest.requestedClientId();
  }

  public ClientAttributes clientAttributes() {
    return clientConfiguration.clientAttributes();
  }

  public Scopes scopes() {
    return backchannelAuthenticationRequest.scopes();
  }

  public CibaProfile profile() {
    return backchannelAuthenticationRequest.profile();
  }

  public AuthorizationDetails authorizationDetails() {
    return backchannelAuthenticationRequest.authorizationDetails();
  }

  public boolean hasOpenidScope() {
    return scopes().contains("openid");
  }

  public boolean hasAnyHint() {
    return backchannelAuthenticationRequest.hasAnyHint();
  }

  public boolean isRequestObjectPattern() {
    return pattern.isRequestParameter();
  }

  public boolean isSupportedGrantTypeWithServer(GrantType grantType) {
    return authorizationServerConfiguration.isSupportedGrantType(grantType);
  }

  public boolean isSupportedGrantTypeWithClient(GrantType grantType) {
    return clientConfiguration.isSupportedGrantType(grantType);
  }

  public AuthenticationInteractionType defaultCibaAuthenticationInteractionType() {
    if (clientConfiguration.hasDefaultCibaAuthenticationInteractionType()) {
      return clientConfiguration.defaultCibaAuthenticationInteractionType();
    }
    return authorizationServerConfiguration.defaultCibaAuthenticationInteractionType();
  }

  public boolean isSupportedUserCode() {
    if (!authorizationServerConfiguration.backchannelUserCodeParameterSupported()) {
      return false;
    }
    return clientConfiguration.backchannelUserCodeParameter();
  }

  public boolean requiredBackchannelAuthUserCode() {
    return authorizationServerConfiguration.requiredBackchannelAuthUserCode();
  }

  public String backchannelAuthUserCodeType() {
    return authorizationServerConfiguration.backchannelAuthUserCodeType();
  }

  public boolean isRequiredIdentityVerification() {
    Scopes scopes = backchannelAuthenticationRequest.scopes();
    return authorizationServerConfiguration.hasRequiredIdentityVerificationScope(
        scopes.toStringSet());
  }

  public Scopes requiredIdentityVerificationScopes() {
    Scopes scopes = backchannelAuthenticationRequest.scopes();
    List<String> requiredIdentityVerificationScope =
        authorizationServerConfiguration.requiredIdentityVerificationScope();
    return scopes.filter(requiredIdentityVerificationScope);
  }

  public TenantIdentifier tenantIdentifier() {
    return tenant.identifier();
  }

  public boolean isFapiProfile() {
    return profile().isFapiCiba();
  }

  public UserHintType userHintType() {
    return backchannelAuthenticationRequest.userHintType();
  }

  public UserHint userHint() {
    return backchannelAuthenticationRequest.userHint();
  }

  public UserHintRelatedParams userHintRelatedParams() {
    HashMap<String, Object> map = new HashMap<>();
    map.put("serverJwks", authorizationServerConfiguration.jwks());

    if (clientConfiguration.hasJwks()) {
      map.put("clientJwks", clientConfiguration.jwks());
    }

    if (clientConfiguration.hasSecret()) {
      map.put("clientSecret", clientConfiguration.clientSecret().value());
    }

    return new UserHintRelatedParams(map);
  }

  public AcrValues acrValues() {
    return backchannelAuthenticationRequest.acrValues();
  }

  public AuthorizationServerConfiguration authorizationServerConfiguration() {
    return authorizationServerConfiguration;
  }

  public List<AuthenticationPolicy> authenticationPolicies() {
    return authorizationServerConfiguration.authenticationPolicies();
  }
}
