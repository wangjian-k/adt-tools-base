/*
 * Copyright (C) 2012 The Android Open Source Project
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
 */

package com.android.sdklib.internal.build;

import com.android.SdkConstants;
import com.android.sdklib.internal.build.SymbolLoader.SymbolEntry;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.google.common.io.Closeables;
import com.google.common.io.Files;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * A class to write R.java classes based on data read from text symbol files generated by
 * aapt with the --output-text-symbols option.
 * 
 * @deprecated Use Android-Builder instead
 */
@Deprecated
public class SymbolWriter {

    private final String mOutFolder;
    private final String mPackageName;
    private final List<SymbolLoader> mSymbols = Lists.newArrayList();
    private final SymbolLoader mValues;

    public SymbolWriter(String outFolder, String packageName, SymbolLoader values) {
        mOutFolder = outFolder;
        mPackageName = packageName;
        mValues = values;
    }

    public void addSymbolsToWrite(SymbolLoader symbols) {
        mSymbols.add(symbols);
    }

    private Table<String, String, SymbolEntry> getAllSymbols() {
        Table<String, String, SymbolEntry> symbols = HashBasedTable.create();

        for (SymbolLoader symbolLoader : mSymbols) {
            symbols.putAll(symbolLoader.getSymbols());
        }

        return symbols;
    }

    public void write() throws IOException {
        Splitter splitter = Splitter.on('.');
        Iterable<String> folders = splitter.split(mPackageName);
        File file = new File(mOutFolder);
        for (String folder : folders) {
            file = new File(file, folder);
        }
        file.mkdirs();
        file = new File(file, SdkConstants.FN_RESOURCE_CLASS);

        BufferedWriter writer = null;
        try {
            writer = Files.newWriter(file, Charsets.UTF_8);

            writer.write("/* AUTO-GENERATED FILE.  DO NOT MODIFY.\n");
            writer.write(" *\n");
            writer.write(" * This class was automatically generated by the\n");
            writer.write(" * aapt tool from the resource data it found.  It\n");
            writer.write(" * should not be modified by hand.\n");
            writer.write(" */\n");

            writer.write("package ");
            writer.write(mPackageName);
            writer.write(";\n\npublic final class R {\n");

            Table<String, String, SymbolEntry> symbols = getAllSymbols();
            Table<String, String, SymbolEntry> values = mValues.getSymbols();

            Set<String> rowSet = symbols.rowKeySet();
            List<String> rowList = Lists.newArrayList(rowSet);
            Collections.sort(rowList);

            for (String row : rowList) {
                writer.write("\tpublic static final class ");
                writer.write(row);
                writer.write(" {\n");

                Map<String, SymbolEntry> rowMap = symbols.row(row);
                Set<String> symbolSet = rowMap.keySet();
                ArrayList<String> symbolList = Lists.newArrayList(symbolSet);
                Collections.sort(symbolList);

                for (String symbolName : symbolList) {
                    // get the matching SymbolEntry from the values Table.
                    SymbolEntry value = values.get(row, symbolName);
                    if (value != null) {
                        writer.write("\t\tpublic static final ");
                        writer.write(value.getType());
                        writer.write(" ");
                        writer.write(value.getName());
                        writer.write(" = ");
                        writer.write(value.getValue());
                        writer.write(";\n");
                    }
                }

                writer.write("\t}\n");
            }

            writer.write("}\n");
        } finally {
          try {
            Closeables.close(writer, true);
          } catch (IOException ignored) {
          }
        }
    }
}
