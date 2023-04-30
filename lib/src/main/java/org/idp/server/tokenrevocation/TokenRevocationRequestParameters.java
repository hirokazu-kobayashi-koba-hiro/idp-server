package org.idp.server.tokenrevocation;

import java.util.List;
import java.util.Map;
import org.idp.server.clientauthenticator.BackchannelRequestParameters;
import org.idp.server.type.ArrayValueMap;
import org.idp.server.type.OAuthRequestKey;
import org.idp.server.type.oauth.*;

/** TokenRevocationRequestParameters */
public class TokenRevocationRequestParameters implements BackchannelRequestParameters {
  ArrayValueMap values;

  public TokenRevocationRequestParameters() {
    this.values = new ArrayValueMap();
  }

  public TokenRevocationRequestParameters(ArrayValueMap values) {
    this.values = values;
  }

  public TokenRevocationRequestParameters(Map<String, String[]> values) {
    this.values = new ArrayValueMap(values);
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public AccessTokenValue accessToken() {
    return new AccessTokenValue(getString(OAuthRequestKey.token));
  }

  public RefreshTokenValue refreshToken() {
    return new RefreshTokenValue(getString(OAuthRequestKey.token));
  }

  public boolean hasToken() {
    return contains(OAuthRequestKey.token);
  }

  public String getString(OAuthRequestKey key) {
    if (!values.contains(key.name())) {
      return "";
    }
    return values.getFirst(key.name());
  }

  boolean contains(OAuthRequestKey key) {
    return values.contains(key.name());
  }

  public List<String> multiValueKeys() {
    return values.multiValueKeys();
  }

  @Override
  public ClientId clientId() {
    return new ClientId(getString(OAuthRequestKey.client_id));
  }

  @Override
  public boolean hasClientId() {
    return contains(OAuthRequestKey.client_id);
  }

  @Override
  public ClientSecret clientSecret() {
    return new ClientSecret(getString(OAuthRequestKey.client_secret));
  }

  @Override
  public boolean hasClientSecret() {
    return contains(OAuthRequestKey.client_secret);
  }

  @Override
  public ClientAssertion clientAssertion() {
    return new ClientAssertion(getString(OAuthRequestKey.client_assertion));
  }

  @Override
  public boolean hasClientAssertion() {
    return contains(OAuthRequestKey.client_assertion);
  }

  @Override
  public ClientAssertionType clientAssertionType() {
    return ClientAssertionType.of(getString(OAuthRequestKey.client_assertion_type));
  }

  @Override
  public boolean hasClientAssertionType() {
    return contains(OAuthRequestKey.client_assertion_type);
  }
}
