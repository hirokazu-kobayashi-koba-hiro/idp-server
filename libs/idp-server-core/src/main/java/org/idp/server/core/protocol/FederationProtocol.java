package org.idp.server.core.protocol;

import org.idp.server.core.federation.FederationDelegate;
import org.idp.server.core.handler.federation.io.FederationCallbackRequest;
import org.idp.server.core.handler.federation.io.FederationCallbackResponse;
import org.idp.server.core.handler.federation.io.FederationRequest;
import org.idp.server.core.handler.federation.io.FederationRequestResponse;

public interface FederationProtocol {

  FederationRequestResponse handleRequest(FederationRequest federationRequest);

  FederationCallbackResponse handleCallback(
      FederationCallbackRequest federationCallbackRequest, FederationDelegate federationDelegate);
}
