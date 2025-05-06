package org.idp.server.core.ciba;

import java.util.HashMap;
import java.util.List;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.type.ciba.Interval;
import org.idp.server.basic.type.ciba.UserCode;
import org.idp.server.basic.type.mtls.ClientCert;
import org.idp.server.basic.type.oauth.*;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.ciba.user.UserHint;
import org.idp.server.core.ciba.user.UserHintRelatedParams;
import org.idp.server.core.ciba.user.UserHintType;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.oidc.client.Client;
import org.idp.server.core.oidc.clientauthenticator.BackchannelRequestContext;
import org.idp.server.core.oidc.clientauthenticator.BackchannelRequestParameters;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;

public class CibaRequestContext implements BackchannelRequestContext {

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
      CibaRequestPattern pattern,
      ClientSecretBasic clientSecretBasic,
      ClientCert clientCert,
      CibaRequestParameters parameters,
      CibaRequestObjectParameters requestObjectParameters,
      JoseContext joseContext,
      BackchannelAuthenticationRequest backchannelAuthenticationRequest,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {
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
    return new Interval(authorizationServerConfiguration.backchannelAuthPollingInterval());
  }

  public ExpiresIn expiresIn() {
    if (backchannelAuthenticationRequest.hasRequestedExpiry()) {
      return new ExpiresIn(backchannelAuthenticationRequest.requestedExpiry().toIntValue());
    }
    return new ExpiresIn(authorizationServerConfiguration.backchannelAuthRequestExpiresIn());
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

  public Client client() {
    return clientConfiguration.client();
  }

  public Scopes scopes() {
    return backchannelAuthenticationRequest.scopes();
  }

  public CibaProfile profile() {
    return backchannelAuthenticationRequest.profile();
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

  public TenantIdentifier tenantIdentifier() {
    return authorizationServerConfiguration.tenantIdentifier();
  }

  public List<String> availableAuthenticationMethods() {
    return authorizationServerConfiguration.availableAuthenticationMethods();
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
}
