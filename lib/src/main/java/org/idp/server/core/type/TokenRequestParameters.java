package org.idp.server.core.type;

import org.idp.server.core.type.oauth.*;

import static org.idp.server.core.type.OAuthRequestKey.*;

import java.util.List;
import java.util.Map;

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
    return new ClientId(getString(client_id));
  }

  public boolean hasClientId() {
    return contains(client_id);
  }

  public ClientSecret clientSecret() {
    return new ClientSecret(getString(client_secret));
  }

  public boolean hasClientSecret() {
    return contains(client_secret);
  }

  public RedirectUri redirectUri() {
    return new RedirectUri(getString(redirect_uri));
  }

  public boolean hasRedirectUri() {
    return contains(redirect_uri);
  }

  public AuthorizationCode code() {
    return new AuthorizationCode(getString(code));
  }

  public boolean hasCode() {
    return contains(code);
  }

  public GrantType grantType() {
    return GrantType.of(getString(grant_type));
  }

  public boolean hasGrantType() {
    return contains(grant_type);
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
