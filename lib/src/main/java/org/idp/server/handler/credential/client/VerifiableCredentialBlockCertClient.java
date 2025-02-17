package org.idp.server.handler.credential.client;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.http.HttpClientFactory;
import org.idp.server.basic.json.JsonConvertable;
import org.idp.server.basic.vc.Credential;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.verifiablecredential.VerifiableCredential;
import org.idp.server.verifiablecredential.VerifiableCredentialCreator;
import org.idp.server.verifiablecredential.exception.VerifiableCredentialBadRequestException;

public class VerifiableCredentialBlockCertClient implements VerifiableCredentialCreator {

  HttpClient httpClient;

  public VerifiableCredentialBlockCertClient() {
    this.httpClient = HttpClientFactory.defaultClient();
  }

  // FIXME setting value
  public VerifiableCredential create(
      Credential credential,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    try {
      HashMap<String, Object> requestBodyMap = new HashMap<>();
      requestBodyMap.put("credential", credential.values());
      String requestBody = JsonConvertable.write(requestBodyMap);
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI("http://localhost:8000/v1/verifiable-credentials/block-cert"))
              .POST((HttpRequest.BodyPublishers.ofString(requestBody)))
              .header("Content-Type", "application/json")
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      Map map = handle(response);
      return new VerifiableCredential(map.get("vc"));
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  Map handle(HttpResponse<String> response) {
    String responseBody = response.body();
    Map map = JsonConvertable.read(responseBody, Map.class);
    switch (response.statusCode()) {
      case 200 -> {
        return map;
      }
      case 400 ->
          throw new VerifiableCredentialBadRequestException(
              "invalid_request", (String) map.get("error_description"));
      default -> throw new RuntimeException("vc error status code: " + response.statusCode());
    }
  }
}
