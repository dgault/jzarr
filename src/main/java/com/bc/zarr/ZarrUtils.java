/*
 * $Id$
 *
 * Copyright (C) 2010 by Brockmann Consult (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package com.bc.zarr;

import com.bc.zarr.storage.Store;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import ucar.ma2.Array;
import ucar.ma2.MAMath;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static com.bc.zarr.ZarrConstants.FILENAME_DOT_ZATTRS;

public final class ZarrUtils {

    private static ObjectMapper objectMapper;

    public static String toJson(Object src) throws JZarrException {
        return toJson(src, false);
    }

    public static String toJson(Object src, boolean prettyPrinting) throws JZarrException {
        try {
            return getObjectWriter(prettyPrinting).writeValueAsString(src);
        } catch (JsonProcessingException e) {
            throw new JZarrException("Unable to convert the source object to json.", e);
        }
    }

    public static void toJson(Object o, Writer writer) throws IOException {
        toJson(o, writer, false);
    }

    public static void toJson(Object o, Writer writer, boolean prettyPrinting) throws IOException {
        getObjectWriter(prettyPrinting).writeValue(writer,o);
    }

    public static int[][] computeChunkIndices(int[] shape, int[] chunks, int[] bufferShape, int[] to) {
        final int depth = shape.length;
        int[] start = new int[depth];
        int[] end = new int[depth];
        int numChunks = 1;
        for (int i = 0; i < depth; i++) {
            final int staIdx = to[i] / chunks[i];
            final int endIdx = (to[i] + bufferShape[i] - 1) / chunks[i];
            numChunks *= (endIdx - staIdx + 1);
            start[i] = staIdx;
            end[i] = endIdx;
        }

        final int[][] chunkIndices = new int[numChunks][];

        final int[] currentIdx = Arrays.copyOf(start, depth);
        for (int i = 0; i < chunkIndices.length; i++) {
            chunkIndices[i] = Arrays.copyOf(currentIdx, depth);
            int depthIdx = depth - 1;
            while (depthIdx >= 0) {
                if (currentIdx[depthIdx] >= end[depthIdx]) {
                    currentIdx[depthIdx] = start[depthIdx];
                    depthIdx--;
                } else {
                    currentIdx[depthIdx]++;
                    depthIdx = -1;
                }
            }
        }
        return chunkIndices;
    }

    public static String createChunkFilename(int[] currentIdx) {
        StringBuilder sb = new StringBuilder();
        for (int aCurrentIdx : currentIdx) {
            sb.append(aCurrentIdx);
            sb.append(".");
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    public static <T> T fromJson(Reader reader, final Class<T> classOfType) throws IOException {
        ObjectMapper objectMapper = getObjectMapper();
        return objectMapper.readValue(reader, classOfType);
    }

    public static long computeSize(int[] ints) {
        long count = 1;
        for (int i : ints) {
            count *= i;
        }
        return count;
    }

    public static int computeSizeInteger(int[] ints) {
        int count = 1;
        for (int i : ints) {
            count *= i;
        }
        return count;
    }

    static ObjectWriter getObjectWriter(boolean prettyPrinting) {
        if (prettyPrinting) {
            DefaultPrettyPrinter defaultPrettyPrinter = new DefaultPrettyPrinter();
            defaultPrettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
            return getObjectMapper().writer(defaultPrettyPrinter);
//            return getObjectMapper().writer().withDefaultPrettyPrinter();
        }
        return getObjectMapper().writer();
    }

    static synchronized ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
        }
        return objectMapper;
    }

    public static void ensureFileExistAndIsReadable(Path dotZGroupPath) throws IOException {
        if (!Files.exists(dotZGroupPath) || Files.isDirectory(dotZGroupPath) || !Files.isReadable(dotZGroupPath)) {
            throw new IOException("File '" + dotZGroupPath.getFileName() + "' is not readable or missing in directory " + dotZGroupPath.getParent() + " .");
        }
    }

    public static void ensureDirectory(Path groupPath) throws IOException {
        if (groupPath == null || !Files.isDirectory(groupPath)) {
            throw new IOException("Path '" + groupPath + "' is not a valid path or not a directory.");
        }
    }

    public static void writeAttributes(Map<String, Object> attributes, ZarrPath zarrPath, Store store) throws IOException {
        if (attributes != null && !attributes.isEmpty()) {
            final ZarrPath attrPath = zarrPath.resolve(FILENAME_DOT_ZATTRS);
            try (
                    final OutputStream os = store.getOutputStream(attrPath.storeKey);
                    final OutputStreamWriter writer = new OutputStreamWriter(os)
            ) {
                toJson(attributes, writer, true);
            }
        }
    }

    public static Map<String, Object> readAttributes(ZarrPath zarrPath, Store store) throws IOException {
        final ZarrPath attrPath = zarrPath.resolve(FILENAME_DOT_ZATTRS);
        try (InputStream inputStream = store.getInputStream(attrPath.storeKey)) {
            if (inputStream == null) {
                return new HashMap<>();
            }
            return fromJson(new InputStreamReader(inputStream), Map.class);
        }
    }

    public static void deleteDirectoryTreeRecursively(Path toBeDeleted) throws IOException {
        final List<Path> paths = Files.walk(toBeDeleted).sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        for (Path path : paths) {
            Files.delete(path);
        }
    }

    public static Object createDataBufferFilledWith(Number value, DataType dataType, int[] shape) {
        final Object dataBuffer = createDataBuffer(dataType, shape);
        ucar.ma2.DataType aType = ucar.ma2.DataType.getType(dataBuffer.getClass().getComponentType(), false);
        Array array = Array.factory(aType, shape, dataBuffer);
        MAMath.setDouble(array, value.doubleValue());
        return array.getStorage();
    }

    public static Object createDataBuffer(DataType dataType, int[] shape) {
        final int size = computeSizeInteger(shape);
        switch (dataType) {
            case i1:
            case u1:
                return new byte[size];
            case i2:
            case u2:
                return new short[size];
            case i4:
            case u4:
                return new int[size];
            case i8:
                return new long[size];
            case f4:
                return new float[size];
            case f8:
                return new double[size];
        }
        return null;
    }
}
