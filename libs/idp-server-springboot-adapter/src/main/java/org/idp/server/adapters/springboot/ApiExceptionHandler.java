/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.adapters.springboot;

import jakarta.servlet.http.HttpServletRequest;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.idp.server.platform.datasource.SqlDuplicateKeyException;
import org.idp.server.platform.exception.*;
import org.idp.server.platform.log.LoggerWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class ApiExceptionHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<?> handleException(BadRequestException exception) {
    log.warn(exception.getMessage(), exception);
    Map<String, String> response =
        Map.of("error", "invalid_request", "error_description", exception.getMessage());
    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<?> handleException(UnauthorizedException exception) {
    log.warn(exception.getMessage());
    Map<String, String> response =
        Map.of("error", "invalid_request", "error_description", exception.getMessage());
    return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<?> handleException(ForbiddenException exception) {
    log.warn(exception.getMessage());
    Map<String, String> response =
        Map.of("error", "invalid_request", "error_description", exception.getMessage());
    return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<?> handleException(NotFoundException exception) {
    log.warn(exception.getMessage());
    Map<String, String> response =
        Map.of("error", "invalid_request", "error_description", exception.getMessage());
    return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<?> handleException(NoResourceFoundException exception) {
    log.warn(exception.getMessage());
    Map<String, String> response =
        Map.of("error", "invalid_request", "error_description", exception.getMessage());
    return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<?> handleException(HttpRequestMethodNotSupportedException exception) {
    log.warn(exception.getMessage(), exception);
    Map<String, String> response =
        Map.of("error", "invalid_request", "error_description", exception.getMessage());
    return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
  }

  @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
  public ResponseEntity<?> handleException(HttpMediaTypeNotAcceptableException ex) {
    log.warn(ex.getMessage());
    Map<String, String> response =
        Map.of("error", "invalid_request", "error_description", ex.getMessage());
    return new ResponseEntity<>(response, HttpStatus.NOT_ACCEPTABLE);
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<?> handleException(ConflictException exception) {
    log.warn(exception.getMessage());
    Map<String, String> response =
        Map.of("error", "invalid_request", "error_description", exception.getMessage());
    return new ResponseEntity<>(response, HttpStatus.CONFLICT);
  }

  @ExceptionHandler(SqlDuplicateKeyException.class)
  public ResponseEntity<?> handleException(SqlDuplicateKeyException exception) {
    log.error(exception.getMessage(), exception);
    Map<String, String> response =
        Map.of(
            "error",
            "invalid_request",
            "error_description",
            "The request could not be completed due to a conflict with existing data.");
    return new ResponseEntity<>(response, HttpStatus.CONFLICT);
  }

  @ExceptionHandler
  public ResponseEntity<?> handleException(
      HttpMediaTypeNotSupportedException exception, HttpServletRequest httpServletRequest) {
    log.warn(exception.getMessage(), exception);
    return new ResponseEntity<>(
        Map.of(
            "error",
            "invalid_request",
            "error_description",
            "Bad request. Content-Type header does not match supported values"),
        HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(HttpMessageConversionException.class)
  public ResponseEntity<?> handleException(HttpMessageConversionException exception) {
    log.warn(exception.getMessage(), exception);
    return new ResponseEntity<>(
        Map.of(
            "error",
            "invalid_request",
            "error_description",
            "The request is malformed or contains invalid parameters"),
        HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(DateTimeParseException.class)
  public ResponseEntity<?> handleException(DateTimeParseException exception) {
    log.warn(exception.getMessage(), exception);
    return new ResponseEntity<>(
        Map.of(
            "error",
            "invalid_request",
            "error_description",
            "Invalid date format. Expected format: YYYY-MM-DD (e.g., 2025-11-24)"),
        HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler
  public ResponseEntity<?> handleException(Exception exception) {
    log.error(exception.getMessage(), exception);
    return new ResponseEntity<>(
        Map.of("error", "server_error", "error_description", "unexpected error is occurred"),
        HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
