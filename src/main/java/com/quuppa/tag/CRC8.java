// Copyright 2022 Quuppa Oy
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

/*
 * Calculate CRC for Quuppa advertisement packets
 */
public class CRC8 {
    public static final byte INITIAL_REGISTER_VALUE = (byte)0x00;

    public static byte simpleCRC(java.io.InputStream s, byte reg) throws java.io.IOException {
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
    public static byte simpleCRC(byte[] buffer, byte register) throws java.io.IOException {
        java.io.ByteArrayInputStream stream = new java.io.ByteArrayInputStream(buffer);
        return simpleCRC(stream, register);
    }
    public static byte simpleCRC(byte[] buffer) throws java.io.IOException {
        return simpleCRC(buffer, INITIAL_REGISTER_VALUE);
    }
}
