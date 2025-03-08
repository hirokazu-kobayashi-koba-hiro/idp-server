package org.idp.server.core;

import org.idp.server.core.api.FederationApi;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.handler.federation.FederationErrorHandler;
import org.idp.server.core.handler.federation.FederationHandler;
import org.idp.server.core.handler.federation.io.FederationCallbackRequest;
import org.idp.server.core.handler.federation.io.FederationCallbackResponse;
import org.idp.server.core.handler.federation.io.FederationRequest;
import org.idp.server.core.handler.federation.io.FederationRequestResponse;

@Transactional
public class FederationApiImpl implements FederationApi {

  FederationHandler federationHandler;
  FederationErrorHandler federationErrorHandler;

  public FederationApiImpl(FederationHandler federationHandler) {
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
      FederationCallbackRequest federationCallbackRequest) {
    try {

      return federationHandler.handleCallback(federationCallbackRequest);
    } catch (Exception e) {

      return federationErrorHandler.handleCallback(e);
    }
  }
}
