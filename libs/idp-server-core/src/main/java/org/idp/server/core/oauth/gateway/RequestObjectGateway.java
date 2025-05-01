package org.idp.server.core.oauth.gateway;

import org.idp.server.basic.type.oauth.RequestUri;
import org.idp.server.basic.type.oidc.RequestObject;

/** RequestObjectGateway */
public interface RequestObjectGateway {
  RequestObject get(RequestUri requestUri);
}
