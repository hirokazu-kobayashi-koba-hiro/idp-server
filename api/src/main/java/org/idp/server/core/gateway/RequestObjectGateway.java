package org.idp.server.core.gateway;

import org.idp.server.core.type.RequestObject;

/** RequestObjectGateway */
public interface RequestObjectGateway {
  RequestObject get(String requestUri);
}
