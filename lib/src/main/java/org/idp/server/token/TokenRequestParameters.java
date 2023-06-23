package org.idp.server.token;

import java.util.List;
import java.util.Map;
import org.idp.server.clientauthenticator.BackchannelRequestParameters;
import org.idp.server.type.ArrayValueMap;
import org.idp.server.type.OAuthRequestKey;
import org.idp.server.type.ciba.AuthReqId;
import org.idp.server.type.oauth.*;
import org.idp.server.type.pkce.CodeVerifier;

/** TokenRequestParameters */
public class TokenRequestParameters implements BackchannelRequestParameters {
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

  @Override
  public ClientId clientId() {
    return new ClientId(getStringOrEmpty(OAuthRequestKey.client_id));
  }

  @Override
  public boolean hasClientId() {
    return contains(OAuthRequestKey.client_id);
  }

  @Override
  public ClientSecret clientSecret() {
    return new ClientSecret(getStringOrEmpty(OAuthRequestKey.client_secret));
  }

  @Override
  public boolean hasClientSecret() {
    return contains(OAuthRequestKey.client_secret);
  }

  @Override
  public ClientAssertion clientAssertion() {
    return new ClientAssertion(getStringOrEmpty(OAuthRequestKey.client_assertion));
  }

  @Override
  public boolean hasClientAssertion() {
    return contains(OAuthRequestKey.client_assertion);
  }

  @Override
  public ClientAssertionType clientAssertionType() {
    return ClientAssertionType.of(getStringOrEmpty(OAuthRequestKey.client_assertion_type));
  }

  @Override
  public boolean hasClientAssertionType() {
    return contains(OAuthRequestKey.client_assertion_type);
  }

  public RedirectUri redirectUri() {
    return new RedirectUri(getStringOrEmpty(OAuthRequestKey.redirect_uri));
  }

  public boolean hasRedirectUri() {
    return contains(OAuthRequestKey.redirect_uri);
  }

  public AuthorizationCode code() {
    return new AuthorizationCode(getStringOrEmpty(OAuthRequestKey.code));
  }

  public boolean hasCode() {
    return contains(OAuthRequestKey.code);
  }

  public GrantType grantType() {
    return GrantType.of(getStringOrEmpty(OAuthRequestKey.grant_type));
  }

  public boolean hasGrantType() {
    return contains(OAuthRequestKey.grant_type);
  }

  public RefreshTokenValue refreshToken() {
    return new RefreshTokenValue(getStringOrEmpty(OAuthRequestKey.refresh_token));
  }

  public boolean hasRefreshToken() {
    return contains(OAuthRequestKey.refresh_token);
  }

  public Scopes scopes() {
    return new Scopes(getStringOrEmpty(OAuthRequestKey.scope));
  }

  public Username username() {
    return new Username(getStringOrEmpty(OAuthRequestKey.username));
  }

  public boolean hasUsername() {
    return contains(OAuthRequestKey.username);
  }

  public Password password() {
    return new Password(getStringOrEmpty(OAuthRequestKey.password));
  }

  public boolean hasPassword() {
    return contains(OAuthRequestKey.password);
  }

  public CodeVerifier codeVerifier() {
    return new CodeVerifier(getStringOrEmpty(OAuthRequestKey.code_verifier));
  }

  public boolean hasCodeVerifier() {
    return contains(OAuthRequestKey.code_verifier);
  }

  public AuthReqId authReqId() {
    return new AuthReqId(getStringOrEmpty(OAuthRequestKey.auth_req_id));
  }

  public boolean hasAuthReqId() {
    return contains(OAuthRequestKey.auth_req_id);
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
