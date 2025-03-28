package org.idp.server.core.adapters.httpclient.oauth;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.idp.server.core.basic.http.HttpClientFactory;
import org.idp.server.core.oauth.gateway.RequestObjectGateway;
import org.idp.server.core.type.oauth.RequestUri;
import org.idp.server.core.type.oidc.RequestObject;

/** RequestObjectHttpClient */
public class RequestObjectHttpClient implements RequestObjectGateway {

  HttpClient httpClient;

  public RequestObjectHttpClient() {
    this.httpClient = HttpClientFactory.defaultClient();
  }

  @Override
  public RequestObject get(RequestUri requestUri) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(requestUri.value()))
              .GET()
              .header("Content-Type", "application/json")
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      String body = response.body();
      return new RequestObject(body);
    } catch (URISyntaxException | IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
