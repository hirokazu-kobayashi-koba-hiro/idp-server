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

package org.idp.server.platform.jose;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jwt.JWTClaimsSet;
import java.text.ParseException;
import org.junit.jupiter.api.Test;

/**
 * Tests for the shared iat/nbf clock-skew validator, including the null-valued-claim path (#1776).
 *
 * <p>This validator is invoked from client_assertion, DPoP proof, Request Object, and attestation
 * CAJ verification. A JWT with {@code iat}/{@code nbf} set to JSON {@code null} previously
 * dereferenced a null {@code Date} here (NPE → 500); the null-aware {@code hasXxx()} now skips it.
 */
class JwtClockSkewValidatorTest {

  private static JsonWebTokenClaims claims(String json) throws ParseException {
    return new JsonWebTokenClaims(JWTClaimsSet.parse(json));
  }

  private static long secondsFromNow(long seconds) {
    return (System.currentTimeMillis() / 1000L) + seconds;
  }

  @Test
  void nullIatDoesNotThrow() throws ParseException {
    assertDoesNotThrow(() -> JwtClockSkewValidator.validateIatNbf(claims("{\"iat\":null}")));
  }

  @Test
  void nullNbfDoesNotThrow() throws ParseException {
    assertDoesNotThrow(() -> JwtClockSkewValidator.validateIatNbf(claims("{\"nbf\":null}")));
  }

  @Test
  void absentIatNbfDoesNotThrow() throws ParseException {
    assertDoesNotThrow(() -> JwtClockSkewValidator.validateIatNbf(claims("{\"sub\":\"c1\"}")));
  }

  @Test
  void iatWithinSkewDoesNotThrow() throws ParseException {
    assertDoesNotThrow(
        () -> JwtClockSkewValidator.validateIatNbf(claims("{\"iat\":" + secondsFromNow(5) + "}")));
  }

  @Test
  void iatTooFarInFutureThrows() throws ParseException {
    assertThrows(
        JwtClockSkewException.class,
        () ->
            JwtClockSkewValidator.validateIatNbf(claims("{\"iat\":" + secondsFromNow(120) + "}")));
  }

  @Test
  void nbfTooFarInFutureThrows() throws ParseException {
    assertThrows(
        JwtClockSkewException.class,
        () ->
            JwtClockSkewValidator.validateIatNbf(claims("{\"nbf\":" + secondsFromNow(120) + "}")));
  }
}
