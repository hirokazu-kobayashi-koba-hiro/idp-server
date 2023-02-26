package org.idp.server.gateway;

import org.idp.server.type.RequestObject;

/** RequestObjectGateway */
public interface RequestObjectGateway {
  RequestObject get(String requestUri);
}
