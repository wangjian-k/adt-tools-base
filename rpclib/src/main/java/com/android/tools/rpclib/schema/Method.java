/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * THIS FILE WAS GENERATED BY codergen. EDIT WITH CARE.
 */
package com.android.tools.rpclib.schema;

import org.jetbrains.annotations.NotNull;
import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import java.io.IOException;

public final class Method {
    public static final byte Bool = 0;
    public static Method bool() { return new Method(Bool); }
    public static final byte Int8 = 1;
    public static Method int8() { return new Method(Int8); }
    public static final byte Uint8 = 2;
    public static Method uint8() { return new Method(Uint8); }
    public static final byte Int16 = 3;
    public static Method int16() { return new Method(Int16); }
    public static final byte Uint16 = 4;
    public static Method uint16() { return new Method(Uint16); }
    public static final byte Int32 = 5;
    public static Method int32() { return new Method(Int32); }
    public static final byte Uint32 = 6;
    public static Method uint32() { return new Method(Uint32); }
    public static final byte Int64 = 7;
    public static Method int64() { return new Method(Int64); }
    public static final byte Uint64 = 8;
    public static Method uint64() { return new Method(Uint64); }
    public static final byte Float32 = 9;
    public static Method float32() { return new Method(Float32); }
    public static final byte Float64 = 10;
    public static Method float64() { return new Method(Float64); }
    public static final byte String = 11;
    public static Method string() { return new Method(String); }

    public final byte value;

    public Method(byte value) {
        this.value = value;
    }

    public void encode(@NotNull Encoder e) throws IOException {
        e.uint8(value);
    }

    public static Method decode(@NotNull Decoder d) throws IOException {
        byte value = d.uint8();
        return new Method(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof Method)) return false;
        return value == ((Method)o).value;
    }

    @Override
    public int hashCode() {
        return value;
    }

    @Override
    public String toString() {
        switch(value) {
            case Bool: return "Bool";
            case Int8: return "Int8";
            case Uint8: return "Uint8";
            case Int16: return "Int16";
            case Uint16: return "Uint16";
            case Int32: return "Int32";
            case Uint32: return "Uint32";
            case Int64: return "Int64";
            case Uint64: return "Uint64";
            case Float32: return "Float32";
            case Float64: return "Float64";
            case String: return "String";
            default: return "Method(" + value + ")";
        }
    }
}
