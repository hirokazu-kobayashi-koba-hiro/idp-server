package org.idp.server.core.oidc.response;

import static org.idp.server.basic.type.oauth.ResponseType.*;
import static org.idp.server.basic.type.oauth.ResponseType.none;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.exception.UnSupportedException;
import org.idp.server.basic.type.oauth.ResponseType;

public class AuthorizationResponseCreators {
  Map<ResponseType, AuthorizationResponseCreator> values;

  public AuthorizationResponseCreators() {
    values = new HashMap<>();
    values.put(code, new AuthorizationResponseCodeCreator());
    values.put(token, new AuthorizationResponseTokenCreator());
    values.put(id_token, new AuthorizationResponseIdTokenCreator());
    values.put(code_token, new AuthorizationResponseCodeTokenCreator());
    values.put(code_token_id_token, new AuthorizationResponseCodeTokenIdTokenCreator());
    values.put(code_id_token, new AuthorizationResponseCodeIdTokenCreator());
    values.put(token_id_token, new AuthorizationResponseTokenIdTokenCreator());
    values.put(vp_token, new AuthorizationResponseVpTokenCreator());
    values.put(vp_token_id_token, new AuthorizationResponseVpTokenIdTokenCreator());
    values.put(none, new AuthorizationResponseNoneCreator());
  }

  public AuthorizationResponseCreator get(ResponseType responseType) {
    AuthorizationResponseCreator authorizationResponseCreator = values.get(responseType);
    if (Objects.isNull(authorizationResponseCreator)) {
      throw new UnSupportedException(String.format("not support request type (%s)", responseType.value()));
    }
    return authorizationResponseCreator;
  }
}
