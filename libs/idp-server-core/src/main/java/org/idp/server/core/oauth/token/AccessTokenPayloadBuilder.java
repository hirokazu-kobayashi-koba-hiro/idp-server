package org.idp.server.core.oauth.token;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.oauth.mtls.ClientCertificationThumbprint;
import org.idp.server.core.oauth.rar.AuthorizationDetails;
import org.idp.server.core.type.extension.CreatedAt;
import org.idp.server.core.type.extension.CustomProperties;
import org.idp.server.core.type.extension.ExpiredAt;
import org.idp.server.core.type.oauth.*;

public class AccessTokenPayloadBuilder {
  Map<String, Object> values = new HashMap<>();

  public AccessTokenPayloadBuilder() {}

  public AccessTokenPayloadBuilder add(TokenIssuer tokenIssuer) {
    values.put("iss", tokenIssuer.value());
    return this;
  }

  public AccessTokenPayloadBuilder add(Subject subject) {
    if (subject.exists()) {
      values.put("sub", subject.value());
    }
    return this;
  }

  public AccessTokenPayloadBuilder add(RequestedClientId requestedClientId) {
    values.put("client_id", requestedClientId.value());
    return this;
  }

  public AccessTokenPayloadBuilder add(Scopes scopes) {
    values.put("scope", scopes.toStringValues());
    return this;
  }

  public AccessTokenPayloadBuilder add(CustomProperties customProperties) {
    if (customProperties.exists()) {
      values.putAll(customProperties.values());
    }
    return this;
  }

  public AccessTokenPayloadBuilder addJti(String jti) {
    values.put("jti", jti);
    return this;
  }

  public AccessTokenPayloadBuilder add(CreatedAt createdAt) {
    values.put("iat", createdAt.toEpochSecondWithUtc());
    return this;
  }

  public AccessTokenPayloadBuilder add(ExpiredAt expiredAt) {
    values.put("exp", expiredAt.toEpochSecondWithUtc());
    return this;
  }

  public AccessTokenPayloadBuilder add(AuthorizationDetails authorizationDetails) {
    if (authorizationDetails.exists()) {
      values.put("authorization_details", authorizationDetails.toMapValues());
    }
    return this;
  }

  public AccessTokenPayloadBuilder add(ClientCertificationThumbprint thumbprint) {
    if (thumbprint.exists()) {
      values.put("cnf", Map.of("x5t#S256", thumbprint.value()));
    }
    return this;
  }

  public Map<String, Object> build() {
    return values;
  }
}
