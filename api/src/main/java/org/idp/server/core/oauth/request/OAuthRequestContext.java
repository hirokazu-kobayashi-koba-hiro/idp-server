package org.idp.server.core.oauth.request;

import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.OAuthRequestPattern;
import org.idp.server.type.OAuthRequestParameters;

/**
 * OAuthRequestContext
 */
public class OAuthRequestContext {
    OAuthRequestPattern pattern;
    OAuthRequestParameters parameters;
    ServerConfiguration serverConfiguration;
    ClientConfiguration clientConfiguration;
}
