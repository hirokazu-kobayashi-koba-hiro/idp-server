package org.idp.server.core.gateway;

import org.idp.server.core.type.oidc.RequestObject;
import org.idp.server.core.type.oauth.RequestUri;

/** RequestObjectGateway */
public interface RequestObjectGateway {
  RequestObject get(RequestUri requestUri);
}
