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

  public AccessToken accessToken() {
    return new AccessToken(getString(OAuthRequestKey.token));
  }

  public RefreshToken refreshToken() {
    return new RefreshToken(getString(OAuthRequestKey.token));
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
}
