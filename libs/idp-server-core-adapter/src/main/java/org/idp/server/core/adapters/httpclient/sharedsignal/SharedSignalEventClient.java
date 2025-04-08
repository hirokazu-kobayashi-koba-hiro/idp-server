package org.idp.server.core.adapters.httpclient.sharedsignal;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Logger;
import org.idp.server.core.basic.http.HttpClientErrorException;
import org.idp.server.core.basic.http.HttpClientFactory;
import org.idp.server.core.basic.http.HttpNetworkErrorException;
import org.idp.server.core.security.hook.ssf.SharedSignalEventGateway;
import org.idp.server.core.security.hook.ssf.SharedSignalEventRequest;

/**
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8935">Push-Based Security Event Token
 *     (SET) Delivery Using HTTP</a>
 */
public class SharedSignalEventClient implements SharedSignalEventGateway {

  HttpClient httpClient;
  Logger log = Logger.getLogger(SharedSignalEventClient.class.getName());

  public SharedSignalEventClient() {
    httpClient = HttpClientFactory.defaultClient();
  }

  @Override
  public void send(SharedSignalEventRequest sharedSignalEventRequest) {
    try {

      HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .uri(new URI(sharedSignalEventRequest.endpoint()))
              .header("Content-Type", "application/secevent+jwt")
              .header("Accept", "application/json")
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      sharedSignalEventRequest.securityEventTokenValue()));

      HttpRequest request = builder.build();

      HttpResponse<String> httpResponse =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      validateResponse(httpResponse);

    } catch (IOException | InterruptedException | URISyntaxException e) {
      log.severe(e.getMessage());
      throw new HttpNetworkErrorException("unexpected network error", e);
    }
  }

  private void validateResponse(HttpResponse<String> httpResponse) {
    String body = httpResponse.body();
    if (httpResponse.statusCode() >= 400 && httpResponse.statusCode() < 500) {

      log.warning("ssf response:" + body);
      throw new HttpClientErrorException(body, httpResponse.statusCode());
    }

    if (httpResponse.statusCode() >= 500) {
      log.severe("ssf response:" + body);
      throw new HttpClientErrorException(body, httpResponse.statusCode());
    }
  }
}
