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


package org.idp.server.platform.date;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LocalDateTimeParser {

    public static LocalDateTime parse(String date) {
        if (date.contains("T")) {
            return LocalDateTime.parse(date);
        }


        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
        return LocalDateTime.parse(normalizeDateTime(date), formatter);
    }

    private static String normalizeDateTime(String dateTimeStr) {
        if (!dateTimeStr.contains(".")) {
            return dateTimeStr + ".000000"; // No fraction present, add six zeroes
        }

        String[] parts = dateTimeStr.split("\\.");
        String fraction = parts[1];

        // Ensure fraction has exactly 6 digits
        if (fraction.length() < 6) {
            fraction = String.format("%-6s", fraction).replace(' ', '0'); // Pad with zeros
        } else if (fraction.length() > 6) {
            fraction = fraction.substring(0, 6); // Trim to 6 digits
        }

        return parts[0] + "." + fraction;
    }
}
