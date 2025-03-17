package org.idp.server.core.protocol;

import org.idp.server.core.handler.discovery.io.JwksRequestResponse;

public interface JwksProtocol {

  JwksRequestResponse getJwks(String issuer);
}
