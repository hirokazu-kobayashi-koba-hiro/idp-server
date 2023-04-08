package org.idp.server.core.gateway;

import org.idp.server.core.type.oauth.RequestUri;
import org.idp.server.core.type.oidc.RequestObject;

/** RequestObjectGateway */
public interface RequestObjectGateway {
  RequestObject get(RequestUri requestUri);
}
