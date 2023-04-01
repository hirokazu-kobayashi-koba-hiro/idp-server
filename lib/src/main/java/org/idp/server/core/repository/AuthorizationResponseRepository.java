package org.idp.server.core.repository;

import org.idp.server.core.oauth.response.AuthorizationResponse;
import org.idp.server.core.type.AuthorizationCode;

public interface AuthorizationResponseRepository {

    AuthorizationResponse register(AuthorizationResponse authorizationResponse);

    AuthorizationResponse find(AuthorizationCode authorizationCode);
}
