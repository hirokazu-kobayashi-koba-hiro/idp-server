package org.idp.server.core.protcol;

import org.idp.server.core.handler.discovery.io.JwksRequestResponse;

public interface JwksApi {

  JwksRequestResponse getJwks(String issuer);
}
