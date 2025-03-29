package org.idp.server.core.ciba;

import static org.idp.server.core.type.OAuthRequestKey.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.clientauthenticator.BackchannelRequestParameters;
import org.idp.server.core.type.OAuthRequestKey;
import org.idp.server.core.type.ciba.*;
import org.idp.server.core.type.oauth.*;
import org.idp.server.core.type.oidc.AcrValues;
import org.idp.server.core.type.oidc.IdTokenHint;
import org.idp.server.core.type.oidc.LoginHint;
import org.idp.server.core.type.oidc.RequestObject;

public class CibaRequestAssembleParameters implements BackchannelRequestParameters {
  Map<String, Object> values;

  public CibaRequestAssembleParameters(
      CibaRequestParameters parameters, CibaRequestObjectParameters requestObjectParameters) {
    Map<String, Object> map = new HashMap<>(parameters.singleValues());
    map.putAll(requestObjectParameters.values());
    this.values = map;
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public Scopes scope() {
    return new Scopes(getValueOrEmpty(scope));
  }

  public boolean hasScope() {
    return contains(scope);
  }

  public ClientNotificationToken clientNotificationToken() {
    return new ClientNotificationToken(getValueOrEmpty(client_notification_token));
  }

  public boolean hasClientNotificationToken() {
    return contains(client_notification_token);
  }

  public UserCode userCode() {
    return new UserCode(getValueOrEmpty(user_code));
  }

  public boolean hasUserCode() {
    return contains(user_code);
  }

  public BindingMessage bindingMessage() {
    return new BindingMessage(getValueOrEmpty(binding_message));
  }

  public boolean hasBindingMessage() {
    return contains(binding_message);
  }

  public LoginHintToken loginHintToken() {
    return new LoginHintToken(getValueOrEmpty(login_hint_token));
  }

  public boolean hasLoginHintToken() {
    return contains(login_hint_token);
  }

  public RequestedExpiry requestedExpiry() {
    return new RequestedExpiry(getValueOrEmpty(requested_expiry));
  }

  public boolean hasRequestedExpiry() {
    return contains(requested_expiry);
  }

  public RequestedClientId clientId() {
    return new RequestedClientId(getValueOrEmpty(client_id));
  }

  public boolean hasClientId() {
    return contains(client_id);
  }

  public IdTokenHint idTokenHint() {
    return new IdTokenHint(getValueOrEmpty(id_token_hint));
  }

  public boolean hasIdTokenHint() {
    return contains(id_token_hint);
  }

  public LoginHint loginHint() {
    return new LoginHint(getValueOrEmpty(login_hint));
  }

  public boolean hasLoginHint() {
    return contains(login_hint);
  }

  public AcrValues acrValues() {
    return new AcrValues(getValueOrEmpty(acr_values));
  }

  public boolean hasAcrValues() {
    return contains(acr_values);
  }

  public RequestObject request() {
    return new RequestObject(getValueOrEmpty(request));
  }

  public boolean hasRequest() {
    return contains(request);
  }

  public RequestUri requestUri() {
    return new RequestUri(getValueOrEmpty(request_uri));
  }

  public boolean hasRequestUri() {
    return contains(request_uri);
  }

  @Override
  public ClientSecret clientSecret() {
    return new ClientSecret(getValueOrEmpty(client_secret));
  }

  @Override
  public boolean hasClientSecret() {
    return contains(client_secret);
  }

  @Override
  public ClientAssertion clientAssertion() {
    return new ClientAssertion(getValueOrEmpty(client_assertion));
  }

  @Override
  public boolean hasClientAssertion() {
    return contains(client_assertion);
  }

  @Override
  public ClientAssertionType clientAssertionType() {
    return ClientAssertionType.of(getValueOrEmpty(client_assertion_type));
  }

  @Override
  public boolean hasClientAssertionType() {
    return contains(client_assertion_type);
  }

  String getValueOrEmpty(OAuthRequestKey key) {
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
