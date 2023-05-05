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
  JoseContext joseContext;
  BackchannelAuthenticationRequest backchannelAuthenticationRequest;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;

  public CibaRequestContext() {}

  public CibaRequestContext(
      CibaRequestPattern pattern,
      ClientSecretBasic clientSecretBasic,
      CibaRequestParameters parameters,
      JoseContext joseContext,
      BackchannelAuthenticationRequest backchannelAuthenticationRequest,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    this.pattern = pattern;
    this.clientSecretBasic = clientSecretBasic;
    this.parameters = parameters;
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
    return parameters;
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
    // FIXME
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
}
