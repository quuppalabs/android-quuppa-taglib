// Copyright 2025 Quuppa Oy
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//    http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.quuppa.tag;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/*
 * Calculate CRC for Quuppa advertisement packets
 */
public class CRC8 {
    public static final byte INITIAL_REGISTER_VALUE = (byte)0x00;

    public static byte simpleCRC(InputStream s, byte reg) throws IOException {
        byte bitMask = (byte)(1 << 7);

        // Process each message byte.
        int value = s.read();
        while (value != -1) {
            byte element = (byte)value;

            reg ^= element;
            for (int i = 0; i < 8; i++) {
                if ((reg & bitMask) != 0) {
                    reg = (byte)((reg << 1) ^ 0x97);
                }
                else {
                    reg <<= 1;
                }
            }
            value = s.read();
        }
        reg ^= 0x00;

        return reg;
    }
    public static byte simpleCRC(byte[] buffer, byte register) throws IOException {
        ByteArrayInputStream stream = new ByteArrayInputStream(buffer);
        return simpleCRC(stream, register);
    }
    public static byte simpleCRC(byte[] buffer) throws IOException {
        return simpleCRC(buffer, INITIAL_REGISTER_VALUE);
    }
}
