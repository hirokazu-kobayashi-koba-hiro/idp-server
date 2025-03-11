package org.idp.server.core.handler.oauth.io;

import java.util.Map;
import org.idp.server.core.oauth.view.OAuthViewData;

public class OAuthViewDataResponse {
  OAuthViewDataStatus status;
  OAuthViewData oAuthViewData;

  public OAuthViewDataResponse(OAuthViewDataStatus status, OAuthViewData oAuthViewData) {
    this.status = status;
    this.oAuthViewData = oAuthViewData;
  }

  public OAuthViewDataStatus status() {
    return status;
  }

  public OAuthViewData oAuthViewData() {
    return oAuthViewData;
  }

  public Map<String, Object> contents() {
    return oAuthViewData.contents();
  }
}
