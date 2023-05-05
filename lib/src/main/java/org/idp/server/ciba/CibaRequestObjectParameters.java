package org.idp.server.ciba;

import static org.idp.server.type.OAuthRequestKey.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.clientauthenticator.BackchannelRequestParameters;
import org.idp.server.type.OAuthRequestKey;
import org.idp.server.type.ciba.*;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.AcrValues;
import org.idp.server.type.oidc.IdTokenHint;
import org.idp.server.type.oidc.LoginHint;
import org.idp.server.type.oidc.RequestObject;

public class CibaRequestObjectParameters implements BackchannelRequestParameters {
  Map<String, Object> values;

  public CibaRequestObjectParameters() {
    this.values = new HashMap<>();
  }

  public CibaRequestObjectParameters(Map<String, Object> values) {
    this.values = values;
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public Scopes scope() {
    return new Scopes(getString(scope));
  }

  public boolean hasScope() {
    return contains(scope);
  }

  public ClientNotificationToken clientNotificationToken() {
    return new ClientNotificationToken(getString(client_notification_token));
  }

  public boolean hasClientNotificationToken() {
    return contains(client_notification_token);
  }

  public UserCode userCode() {
    return new UserCode(getString(user_code));
  }

  public boolean hasUserCode() {
    return contains(user_code);
  }

  public BindingMessage bindingMessage() {
    return new BindingMessage(getString(binding_message));
  }

  public boolean hasBindingMessage() {
    return contains(binding_message);
  }

  public LoginHintToken loginHintToken() {
    return new LoginHintToken(getString(login_hint_token));
  }

  public boolean hasLoginHintToken() {
    return contains(login_hint_token);
  }

  public RequestedExpiry requestedExpiry() {
    return new RequestedExpiry(getString(requested_expiry));
  }

  public boolean hasRequestedExpiry() {
    return contains(requested_expiry);
  }

  public ClientId clientId() {
    return new ClientId(getString(client_id));
  }

  public boolean hasClientId() {
    return contains(client_id);
  }

  public IdTokenHint idTokenHint() {
    return new IdTokenHint(getString(id_token_hint));
  }

  public boolean hasIdTokenHint() {
    return contains(id_token_hint);
  }

  public LoginHint loginHint() {
    return new LoginHint(getString(login_hint));
  }

  public boolean hasLoginHint() {
    return contains(login_hint);
  }

  public AcrValues acrValues() {
    return new AcrValues(getString(acr_values));
  }

  public boolean hasAcrValues() {
    return contains(acr_values);
  }

  public RequestObject request() {
    return new RequestObject(getString(request));
  }

  public boolean hasRequest() {
    return contains(request);
  }

  public RequestUri requestUri() {
    return new RequestUri(getString(request_uri));
  }

  public boolean hasRequestUri() {
    return contains(request_uri);
  }

  @Override
  public ClientSecret clientSecret() {
    return new ClientSecret(getString(client_secret));
  }

  @Override
  public boolean hasClientSecret() {
    return contains(client_secret);
  }

  @Override
  public ClientAssertion clientAssertion() {
    return new ClientAssertion(getString(client_assertion));
  }

  @Override
  public boolean hasClientAssertion() {
    return contains(client_assertion);
  }

  @Override
  public ClientAssertionType clientAssertionType() {
    return ClientAssertionType.of(getString(client_assertion_type));
  }

  @Override
  public boolean hasClientAssertionType() {
    return contains(client_assertion_type);
  }

  String getString(OAuthRequestKey key) {
    Object value = values.get(key.name());
    if (Objects.isNull(value)) {
      return "";
    }
    return (String) value;
  }

  boolean contains(OAuthRequestKey key) {
    return values.containsKey(key.name());
  }
}
