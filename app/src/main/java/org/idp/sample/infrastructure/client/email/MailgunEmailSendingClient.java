package org.idp.sample.infrastructure.client.email;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.idp.sample.domain.model.notification.EmailSendingRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Deprecated
public class MailgunEmailSendingClient {

  String username;
  String password;
  RestTemplate restTemplate;
  Logger log = LoggerFactory.getLogger(MailgunEmailSendingClient.class);

  public MailgunEmailSendingClient(
      @Value("${idp.configurations.email.url}") String url,
      @Value("${idp.configurations.email.username}") String username,
      @Value("${idp.configurations.email.apiKey}") String password) {
    this.username = username;
    this.password = password;
    this.restTemplate =
        new RestTemplateBuilder()
            .rootUri(url)
            .errorHandler(new EmailSendingClientErrorHandler())
            .readTimeout(Duration.of(30, ChronoUnit.SECONDS))
            .build();
  }

  public void send(EmailSendingRequest request) {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    httpHeaders.setBasicAuth(username, password);

    MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
    requestBody.add("from", request.from());
    requestBody.add("to", request.to());
    requestBody.add("subject", request.subject());
    requestBody.add("text", request.body());

    ResponseEntity<String> exchange =
        restTemplate.exchange(
            "/messages", HttpMethod.POST, new HttpEntity<>(requestBody, httpHeaders), String.class);
    String body = exchange.getBody();

    log.info(body);
  }
}
