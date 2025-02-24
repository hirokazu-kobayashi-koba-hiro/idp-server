package org.idp.sample.infrastructure.client.email;

import java.io.IOException;
import java.net.URI;
import org.idp.sample.domain.model.notification.EmailSendingTooManyRequestsException;
import org.idp.sample.domain.model.notification.exception.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;

public class EmailSendingClientErrorHandler implements ResponseErrorHandler {

  @Override
  public boolean hasError(ClientHttpResponse response) throws IOException {
    return response.getStatusCode().isError();
  }

  @Override
  public void handleError(URI url, HttpMethod method, ClientHttpResponse response)
      throws IOException {
    HttpStatusCode statusCode = response.getStatusCode();

    switch (statusCode) {
      case HttpStatus.BAD_REQUEST:
        throw new EmailSendingBadRequestException("Bad request");
      case HttpStatus.UNAUTHORIZED:
        throw new EmailSendingUnauthorizedException("Unauthorized");
      case HttpStatus.FORBIDDEN:
        throw new EmailSendingForbiddenException("Forbidden");
      case HttpStatus.NOT_FOUND:
        throw new EmailSendingNotFoundException(url.toString() + "is not found");
      case HttpStatus.REQUEST_TIMEOUT:
        throw new EmailSendingTimeoutException("timeout");
      case HttpStatus.TOO_MANY_REQUESTS:
        throw new EmailSendingTooManyRequestsException("too many requests");
      case HttpStatus.SERVICE_UNAVAILABLE:
        throw new EmailSendingMaintenanceException("service unavailable");
      default:
        throw new EmailSendingInternalServerErrorException(url.toString());
    }
  }
}
