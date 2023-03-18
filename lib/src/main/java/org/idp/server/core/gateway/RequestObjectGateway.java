package org.idp.server.core.gateway;

import org.idp.server.core.type.RequestObject;
import org.idp.server.core.type.RequestUri;

/** RequestObjectGateway */
public interface RequestObjectGateway {
  RequestObject get(RequestUri requestUri);
}
