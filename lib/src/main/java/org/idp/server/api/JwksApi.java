package org.idp.server.api;

import org.idp.server.handler.discovery.io.JwksRequestResponse;

public interface JwksApi {

  JwksRequestResponse getJwks(String issuer);
}
