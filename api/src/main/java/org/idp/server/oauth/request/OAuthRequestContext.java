package org.idp.server.oauth.request;

import org.idp.server.basic.jose.JoseContext;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.OAuthRequestPattern;
import org.idp.server.type.OAuthRequestParameters;

/** OAuthRequestContext */
public class OAuthRequestContext {
  OAuthRequestPattern pattern;
  OAuthRequestParameters parameters;
  JoseContext joseContext;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;
}
