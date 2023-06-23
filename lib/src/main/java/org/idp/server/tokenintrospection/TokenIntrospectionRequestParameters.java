package org.idp.server.tokenintrospection;

import java.util.List;
import java.util.Map;
import org.idp.server.type.ArrayValueMap;
import org.idp.server.type.OAuthRequestKey;
import org.idp.server.type.oauth.*;

/** TokenRequestParameters */
public class TokenIntrospectionRequestParameters {
  ArrayValueMap values;

  public TokenIntrospectionRequestParameters() {
    this.values = new ArrayValueMap();
  }

  public TokenIntrospectionRequestParameters(ArrayValueMap values) {
    this.values = values;
  }

  public TokenIntrospectionRequestParameters(Map<String, String[]> values) {
    this.values = new ArrayValueMap(values);
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public AccessTokenValue accessToken() {
    return new AccessTokenValue(getStringOrEmpty(OAuthRequestKey.token));
  }

  public RefreshTokenValue refreshToken() {
    return new RefreshTokenValue(getStringOrEmpty(OAuthRequestKey.token));
  }

  public boolean hasToken() {
    return contains(OAuthRequestKey.token);
  }

  public String getStringOrEmpty(OAuthRequestKey key) {
    return values.getFirstOrEmpty(key.name());
  }

  boolean contains(OAuthRequestKey key) {
    return values.contains(key.name());
  }

  public List<String> multiValueKeys() {
    return values.multiValueKeys();
  }
}
