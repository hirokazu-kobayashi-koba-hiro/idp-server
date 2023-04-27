package org.idp.server.clientauthenticator;

import org.idp.server.type.oauth.ClientAssertion;
import org.idp.server.type.oauth.ClientAssertionType;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.ClientSecret;

public interface BackchannelRequestParameters {
  ClientId clientId();

  boolean hasClientId();

  ClientSecret clientSecret();

  boolean hasClientSecret();

  ClientAssertion clientAssertion();

  boolean hasClientAssertion();

  ClientAssertionType clientAssertionType();

  boolean hasClientAssertionType();
}
