package org.idp.server.core.oidc.clientauthenticator;

import org.idp.server.basic.type.oauth.ClientAssertion;
import org.idp.server.basic.type.oauth.ClientAssertionType;
import org.idp.server.basic.type.oauth.ClientSecret;
import org.idp.server.basic.type.oauth.RequestedClientId;

public interface BackchannelRequestParameters {
  RequestedClientId clientId();

  boolean hasClientId();

  ClientSecret clientSecret();

  boolean hasClientSecret();

  ClientAssertion clientAssertion();

  boolean hasClientAssertion();

  ClientAssertionType clientAssertionType();

  boolean hasClientAssertionType();
}
