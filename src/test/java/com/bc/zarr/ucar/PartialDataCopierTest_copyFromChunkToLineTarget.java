package com.bc.zarr.ucar;

import org.junit.Before;
import org.junit.Test;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class PartialDataCopierTest_copyFromChunkToLineTarget {

    private Array targetLine;
    private Array sourceChunk;

    @Before
    public void setUp() {
        sourceChunk = Array.factory(DataType.INT, new int[]{4, 5}, new int[]{
                0, 1, 2, 3, 4,
                5, 6, 7, 8, 9,
                10, 11, 12, 13, 14,
                15, 16, 17, 18, 19
        });

        targetLine = Array.factory(DataType.INT, new int[]{1, 16}, new int[]{
                40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40
        });
    }

    @Test
    public void name() throws InvalidRangeException {
        final int[] to = {2, -4};

        PartialDataCopier.copy(to, sourceChunk, targetLine);

        assertThat((int[]) targetLine.copyTo1DJavaArray(), equalTo(new int[]{
                40, 40, 40, 40, 10, 11, 12, 13, 14, 40, 40, 40, 40, 40, 40, 40
        }));
    }
}