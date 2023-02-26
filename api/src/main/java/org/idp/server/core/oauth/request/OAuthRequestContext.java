package org.idp.server.core.oauth.request;

import org.idp.server.basic.jose.JoseContext;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.AuthorizationProfile;
import org.idp.server.core.oauth.OAuthRequestPattern;
import org.idp.server.core.type.OAuthRequestParameters;

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
