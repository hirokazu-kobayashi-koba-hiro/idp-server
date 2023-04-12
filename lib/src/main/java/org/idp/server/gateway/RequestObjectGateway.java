package org.idp.server.gateway;

import org.idp.server.type.oauth.RequestUri;
import org.idp.server.type.oidc.RequestObject;

/** RequestObjectGateway */
public interface RequestObjectGateway {
  RequestObject get(RequestUri requestUri);
}
