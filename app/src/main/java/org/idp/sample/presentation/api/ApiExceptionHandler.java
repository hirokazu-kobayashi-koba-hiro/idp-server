package org.idp.sample.presentation.api;

import org.idp.sample.domain.model.base.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class ApiExceptionHandler {

  Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler
  public ResponseEntity<?> handleException(BadRequestException exception) {
    log.warn(exception.getMessage());
    return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler
  public ResponseEntity<?> handleException(UnauthorizedException exception) {
    log.warn(exception.getMessage());
    return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
  }

  @ExceptionHandler
  public ResponseEntity<?> handleException(ForbiddenException exception) {
    log.warn(exception.getMessage());
    return new ResponseEntity<>(HttpStatus.FORBIDDEN);
  }

  @ExceptionHandler
  public ResponseEntity<?> handleException(NotFoundException exception) {
    log.warn(exception.getMessage());
    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler
  public ResponseEntity<?> handleException(ConflictException exception) {
    log.warn(exception.getMessage());
    return new ResponseEntity<>(HttpStatus.CONFLICT);
  }
}
