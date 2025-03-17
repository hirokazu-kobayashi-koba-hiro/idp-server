package org.idp.server.core.protocol;

import org.idp.server.core.federation.FederationDelegate;
import org.idp.server.core.handler.federation.FederationErrorHandler;
import org.idp.server.core.handler.federation.FederationHandler;
import org.idp.server.core.handler.federation.io.FederationCallbackRequest;
import org.idp.server.core.handler.federation.io.FederationCallbackResponse;
import org.idp.server.core.handler.federation.io.FederationRequest;
import org.idp.server.core.handler.federation.io.FederationRequestResponse;

public class FederationProtocolImpl implements FederationProtocol {

  FederationHandler federationHandler;
  FederationErrorHandler federationErrorHandler;

  public FederationProtocolImpl(FederationHandler federationHandler) {
    this.federationHandler = federationHandler;
    this.federationErrorHandler = new FederationErrorHandler();
  }

  @Override
  public FederationRequestResponse handleRequest(FederationRequest federationRequest) {
    try {

      return federationHandler.handleRequest(federationRequest);
    } catch (Exception e) {

      return federationErrorHandler.handleRequest(e);
    }
  }

  @Override
  public FederationCallbackResponse handleCallback(
      FederationCallbackRequest federationCallbackRequest, FederationDelegate federationDelegate) {
    try {

      return federationHandler.handleCallback(federationCallbackRequest, federationDelegate);
    } catch (Exception e) {

      return federationErrorHandler.handleCallback(e);
    }
  }
}
