package org.idp.server.ciba;

import org.idp.server.basic.jose.JoseContext;
import org.idp.server.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.clientauthenticator.BackchannelRequestContext;
import org.idp.server.clientauthenticator.BackchannelRequestParameters;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.type.ciba.Interval;
import org.idp.server.type.ciba.UserCode;
import org.idp.server.type.oauth.*;

public class CibaRequestContext implements BackchannelRequestContext {

  CibaRequestPattern pattern;
  ClientSecretBasic clientSecretBasic;
  CibaRequestParameters parameters;
  CibaRequestObjectParameters requestObjectParameters;
  CibaRequestAssembleParameters assembleParameters;
  JoseContext joseContext;
  BackchannelAuthenticationRequest backchannelAuthenticationRequest;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;

  public CibaRequestContext() {}

  public CibaRequestContext(
      CibaRequestPattern pattern,
      ClientSecretBasic clientSecretBasic,
      CibaRequestParameters parameters,
      CibaRequestObjectParameters requestObjectParameters,
      JoseContext joseContext,
      BackchannelAuthenticationRequest backchannelAuthenticationRequest,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    this.pattern = pattern;
    this.clientSecretBasic = clientSecretBasic;
    this.parameters = parameters;
    this.requestObjectParameters = requestObjectParameters;
    this.assembleParameters =
        new CibaRequestAssembleParameters(parameters, requestObjectParameters);
    this.joseContext = joseContext;
    this.backchannelAuthenticationRequest = backchannelAuthenticationRequest;
    this.serverConfiguration = serverConfiguration;
    this.clientConfiguration = clientConfiguration;
  }

  public CibaRequestPattern pattern() {
    return pattern;
  }

  public TokenIssuer tokenIssuer() {
    return serverConfiguration.tokenIssuer();
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
  public boolean hasClientSecretBasic() {
    return clientSecretBasic.exists();
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

  public BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier() {
    return backchannelAuthenticationRequest.identifier();
  }

  public Interval interval() {
    // FIXME
    return new Interval(3);
  }

  public ExpiresIn expiresIn() {
    if (backchannelAuthenticationRequest.hasRequestedExpiry()) {
      return new ExpiresIn(backchannelAuthenticationRequest.requestedExpiry().toIntValue());
    }
    return new ExpiresIn(300);
  }

  public boolean hasUserCode() {
    return backchannelAuthenticationRequest.hasUserCode();
  }

  public UserCode userCode() {
    return backchannelAuthenticationRequest.userCode();
  }

  public ClientId clientId() {
    return clientConfiguration.clientId();
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
}
