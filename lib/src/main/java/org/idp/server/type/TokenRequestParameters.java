package org.idp.server.type;

import java.util.List;
import java.util.Map;
import org.idp.server.type.oauth.*;

/** TokenRequestParameters */
public class TokenRequestParameters {
  ArrayValueMap values;

  public TokenRequestParameters() {
    this.values = new ArrayValueMap();
  }

  public TokenRequestParameters(ArrayValueMap values) {
    this.values = values;
  }

  public TokenRequestParameters(Map<String, String[]> values) {
    this.values = new ArrayValueMap(values);
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public ClientId clientId() {
    return new ClientId(getString(OAuthRequestKey.client_id));
  }

  public boolean hasClientId() {
    return contains(OAuthRequestKey.client_id);
  }

  public ClientSecret clientSecret() {
    return new ClientSecret(getString(OAuthRequestKey.client_secret));
  }

  public boolean hasClientSecret() {
    return contains(OAuthRequestKey.client_secret);
  }

  public RedirectUri redirectUri() {
    return new RedirectUri(getString(OAuthRequestKey.redirect_uri));
  }

  public boolean hasRedirectUri() {
    return contains(OAuthRequestKey.redirect_uri);
  }

  public AuthorizationCode code() {
    return new AuthorizationCode(getString(OAuthRequestKey.code));
  }

  public boolean hasCode() {
    return contains(OAuthRequestKey.code);
  }

  public GrantType grantType() {
    return GrantType.of(getString(OAuthRequestKey.grant_type));
  }

  public boolean hasGrantType() {
    return contains(OAuthRequestKey.grant_type);
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
