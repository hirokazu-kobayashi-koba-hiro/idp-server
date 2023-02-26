package org.idp.server.oauth.request;

import org.idp.server.basic.jose.JoseContext;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.AuthorizationProfile;
import org.idp.server.oauth.OAuthRequestPattern;
import org.idp.server.type.OAuthRequestParameters;

/** OAuthRequestContext */
public class OAuthRequestContext {
  AuthorizationProfile profile;
  OAuthRequestPattern pattern;
  OAuthRequestParameters parameters;
  JoseContext joseContext;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;

  public OAuthRequestContext() {}

  public OAuthRequestContext(
      AuthorizationProfile profile,
      OAuthRequestPattern pattern,
      OAuthRequestParameters parameters,
      JoseContext joseContext,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    this.profile = profile;
    this.pattern = pattern;
    this.parameters = parameters;
    this.joseContext = joseContext;
    this.serverConfiguration = serverConfiguration;
    this.clientConfiguration = clientConfiguration;
  }

  public AuthorizationProfile profile() {
    return profile;
  }

  public OAuthRequestPattern pattern() {
    return pattern;
  }

  public OAuthRequestParameters parameters() {
    return parameters;
  }

  public JoseContext joseContext() {
    return joseContext;
  }

  public ServerConfiguration serverConfiguration() {
    return serverConfiguration;
  }

  public ClientConfiguration clientConfiguration() {
    return clientConfiguration;
  }
}
