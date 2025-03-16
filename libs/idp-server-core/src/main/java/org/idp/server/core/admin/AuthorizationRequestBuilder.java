package org.idp.server.core.admin;

import java.util.Map;
import org.idp.server.core.basic.http.QueryParams;

public class AuthorizationRequestBuilder {
  String authorizationEndpoint;
  String clientId;
  String redirectUri;
  String scope;
  String responseType;
  Map<String, String> customParameters;
  QueryParams queryParams = new QueryParams();

  public AuthorizationRequestBuilder(
      String authorizationEndpoint,
      String clientId,
      String redirectUri,
      String scope,
      String responseType,
      Map<String, String> customParameters) {
    this.authorizationEndpoint = authorizationEndpoint;
    this.clientId = clientId;
    this.redirectUri = redirectUri;
    this.scope = scope;
    this.responseType = responseType;
    this.customParameters = customParameters;
  }

  public String build() {
    return "";
  }
}
