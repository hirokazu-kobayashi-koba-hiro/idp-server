package org.idp.server.core.oauth.request;

import org.idp.server.basic.jose.JoseContext;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.OAuthRequestPattern;
import org.idp.server.core.type.OAuthRequestParameters;

/** OAuthRequestContext */
public class OAuthRequestContext {
  OAuthRequestPattern pattern;
  OAuthRequestParameters parameters;
  JoseContext joseContext;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;
}
