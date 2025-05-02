package org.idp.server.adapters.springboot.restapi;

import java.util.Map;
import org.idp.server.basic.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class ApiExceptionHandler {

  Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<?> handleException(BadRequestException exception) {
    log.warn(exception.getMessage());
    return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<?> handleException(UnauthorizedException exception) {
    log.warn(exception.getMessage());
    return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<?> handleException(ForbiddenException exception) {
    log.warn(exception.getMessage());
    return new ResponseEntity<>(HttpStatus.FORBIDDEN);
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<?> handleException(NotFoundException exception) {
    log.warn(exception.getMessage());
    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<?> handleException(ConflictException exception) {
    log.warn(exception.getMessage());
    return new ResponseEntity<>(HttpStatus.CONFLICT);
  }

  @ExceptionHandler
  public ResponseEntity<?> handleException(Exception exception) {
    log.error(exception.getMessage(), exception);
    return new ResponseEntity<>(
        Map.of("error", "server_error", "error_description", "unexpected error is occurred"),
        HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
