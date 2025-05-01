package org.idp.server.core.token.tokenrevocation;

import java.util.List;
import java.util.Map;
import org.idp.server.core.clientauthenticator.BackchannelRequestParameters;
import org.idp.server.basic.type.ArrayValueMap;
import org.idp.server.basic.type.OAuthRequestKey;
import org.idp.server.basic.type.oauth.*;

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

  public AccessTokenEntity accessToken() {
    return new AccessTokenEntity(getValueOrEmpty(OAuthRequestKey.token));
  }

  public RefreshTokenEntity refreshToken() {
    return new RefreshTokenEntity(getValueOrEmpty(OAuthRequestKey.token));
  }

  public boolean hasToken() {
    return contains(OAuthRequestKey.token);
  }

  public String getValueOrEmpty(OAuthRequestKey key) {
    return values.getFirstOrEmpty(key.name());
  }

  boolean contains(OAuthRequestKey key) {
    return values.contains(key.name());
  }

  public List<String> multiValueKeys() {
    return values.multiValueKeys();
  }

  @Override
  public RequestedClientId clientId() {
    return new RequestedClientId(getValueOrEmpty(OAuthRequestKey.client_id));
  }

  @Override
  public boolean hasClientId() {
    return contains(OAuthRequestKey.client_id);
  }

  @Override
  public ClientSecret clientSecret() {
    return new ClientSecret(getValueOrEmpty(OAuthRequestKey.client_secret));
  }

  @Override
  public boolean hasClientSecret() {
    return contains(OAuthRequestKey.client_secret);
  }

  @Override
  public ClientAssertion clientAssertion() {
    return new ClientAssertion(getValueOrEmpty(OAuthRequestKey.client_assertion));
  }

  @Override
  public boolean hasClientAssertion() {
    return contains(OAuthRequestKey.client_assertion);
  }

  @Override
  public ClientAssertionType clientAssertionType() {
    return ClientAssertionType.of(getValueOrEmpty(OAuthRequestKey.client_assertion_type));
  }

  @Override
  public boolean hasClientAssertionType() {
    return contains(OAuthRequestKey.client_assertion_type);
  }
}
