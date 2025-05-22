/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.adapters.springboot.application.restapi;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.platform.exception.*;
import org.idp.server.platform.log.LoggerWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class ApiExceptionHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(ApiExceptionHandler.class);

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

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<?> handleException(NoResourceFoundException exception) {
    log.warn(exception.getMessage());
    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<?> handleException(ConflictException exception) {
    log.warn(exception.getMessage());
    return new ResponseEntity<>(HttpStatus.CONFLICT);
  }

  @ExceptionHandler
  public ResponseEntity<?> handleException(
      HttpMediaTypeNotSupportedException exception, HttpServletRequest httpServletRequest) {
    log.warn(exception.getMessage(), exception);
    return new ResponseEntity<>(
        Map.of("error", "client_error", "error_description", "please check media type"),
        HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @ExceptionHandler
  public ResponseEntity<?> handleException(Exception exception) {
    log.error(exception.getMessage(), exception);
    return new ResponseEntity<>(
        Map.of("error", "server_error", "error_description", "unexpected error is occurred"),
        HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
