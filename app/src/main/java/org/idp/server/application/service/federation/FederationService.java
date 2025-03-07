package org.idp.server.application.service.federation;

import org.idp.server.core.IdpServerApplication;
import org.idp.server.core.api.FederationApi;
import org.idp.server.core.handler.federation.io.FederationCallbackRequest;
import org.idp.server.core.handler.federation.io.FederationCallbackResponse;
import org.idp.server.core.handler.federation.io.FederationRequest;
import org.idp.server.core.handler.federation.io.FederationRequestResponse;
import org.springframework.stereotype.Service;

@Service
public class FederationService {

  FederationApi federationApi;

  public FederationService(IdpServerApplication idpServerApplication) {
    this.federationApi = idpServerApplication.federationApi();
  }

  public FederationRequestResponse request(FederationRequest federationRequest) {

    return federationApi.handleRequest(federationRequest);
  }

  public FederationCallbackResponse callback(FederationCallbackRequest federationCallbackRequest) {

    return federationApi.handleCallback(federationCallbackRequest);
  }
}
