/*-
 * #%L
 * com.oceanbase:obkv-hbase-client
 * %%
 * Copyright (C) 2022 - 2024 OceanBase Group
 * %%
 * OBKV HBase Client Framework  is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 *          http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
 * EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
 * MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 * #L%
 */

package com.alipay.oceanbase.hbase;

import com.alipay.oceanbase.rpc.mutation.result.MutationResult;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.client.coprocessor.Batch;
import org.apache.hadoop.hbase.filter.PrefixFilter;
import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.util.*;

import static org.apache.hadoop.hbase.util.Bytes.toBytes;
import static org.junit.Assert.*;

public class OHTableMultiColumnFamilyTest {
    @Rule
    public ExpectedException  expectedException = ExpectedException.none();

    protected Table hTable;

    @Before
    public void before() throws Exception {
        hTable = ObHTableTestUtil.newOHTableClient("test_multi_cf");
        ((OHTableClient) hTable).init();
    }

    @After
    public void finish() throws IOException {
        hTable.close();
    }

    @Test
    public void testMultiColumnFamilyBatch() throws Exception {
        byte[] family1 = "family_with_group1".getBytes();
        byte[] family2 = "family_with_group2".getBytes();
        byte[] family3 = "family_with_group3".getBytes();

        byte[] family1_column1 = "family1_column1".getBytes();
        byte[] family1_column2 = "family1_column2".getBytes();
        byte[] family1_column3 = "family1_column3".getBytes();
        byte[] family2_column1 = "family2_column1".getBytes();
        byte[] family2_column2 = "family2_column2".getBytes();
        byte[] family3_column1 = "family3_column1".getBytes();
        byte[] family1_value = "VVV1".getBytes();
        byte[] family2_value = "VVV2".getBytes();
        byte[] family3_value = "VVV3".getBytes();

        int rows = 10;
        List<Row> batchLsit = new LinkedList<>();
        for (int i = 0; i < rows; ++i) {
            Put put = new Put(toBytes("Key" + i));
            Delete delete = new Delete(toBytes("Key" + i));
            batchLsit.add(delete);
            put.addColumn(family1, family1_column1, family1_value);
            put.addColumn(family1, family1_column2, family1_value);
            put.addColumn(family1, family1_column3, family1_value);
            put.addColumn(family2, family2_column1, family2_value);
            put.addColumn(family2, family2_column2, family2_value);
            put.addColumn(family3, family3_column1, family3_value);
            batchLsit.add(put);
        }

        // f1c1 f1c2 f1c3 f2c1 f2c2 f3c1
        Delete delete = new Delete(toBytes("Key1"));
        delete.addColumns(family1, family1_column1);
        delete.addColumns(family2, family2_column1);
        batchLsit.add(delete);
        Object[] results = new Object[batchLsit.size()];
        hTable.batch(batchLsit, results);
        // f1c2 f1c3 f2c2 f3c1
        Get get = new Get(toBytes("Key1"));
        Result result = hTable.get(get);
        Cell[] keyValues = result.rawCells();
        assertEquals(4, keyValues.length);
        assertFalse(result.containsColumn(family1, family1_column1));
        assertFalse(result.containsColumn(family2, family2_column1));

        assertTrue(result.containsColumn(family1, family1_column2));
        assertArrayEquals(result.getValue(family1, family1_column2), family1_value);
        assertTrue(result.containsColumn(family1, family1_column3));
        assertArrayEquals(result.getValue(family1, family1_column3), family1_value);
        assertTrue(result.containsColumn(family2, family2_column2));
        assertArrayEquals(result.getValue(family2, family2_column2), family2_value);
        assertTrue(result.containsColumn(family3, family3_column1));
        assertArrayEquals(result.getValue(family3, family3_column1), family3_value);

        // f1c1 f2c1 f2c2
        delete = new Delete(toBytes("Key2"));
        delete.addColumns(family1, family1_column2);
        delete.addColumns(family1, family1_column3);
        delete.addColumns(family3, family3_column1);
        batchLsit.add(delete);
        // null
        results = new Object[batchLsit.size()];
        hTable.batch(batchLsit, results);
        get = new Get(toBytes("Key2"));
        result = hTable.get(get);
        keyValues = result.rawCells();
        assertEquals(3, keyValues.length);
        batchLsit.clear();
        for (int i = 0; i < rows; ++i) {
            Put put = new Put(toBytes("Key" + i));
            put.addColumn(family1, family1_column1, family1_value);
            put.addColumn(family1, family1_column2, family1_value);
            put.addColumn(family1, family1_column3, family1_value);
            put.addColumn(family2, family2_column1, family2_value);
            put.addColumn(family2, family2_column2, family2_value);
            put.addColumn(family3, family3_column1, family3_value);
            batchLsit.add(put);
        }

        delete = new Delete(toBytes("Key3"));
        delete.addColumn(family1, family1_column2);
        delete.addColumn(family2, family2_column1);
        batchLsit.add(delete);
        results = new Object[batchLsit.size()];
        hTable.batch(batchLsit, results);
        get = new Get(toBytes("Key3"));
        result = hTable.get(get);
        keyValues = result.rawCells();
        assertEquals(6, keyValues.length);

        batchLsit.clear();
        delete = new Delete(toBytes("Key4"));
        delete.addColumns(family1, family1_column2);
        delete.addColumns(family2, family2_column1);
        delete.addFamily(family3);
        batchLsit.add(delete);
        results = new Object[batchLsit.size()];
        hTable.batch(batchLsit, results);
        get = new Get(toBytes("Key4"));
        get.setMaxVersions(10);
        result = hTable.get(get);
        keyValues = result.rawCells();
        assertEquals(6, keyValues.length);

        batchLsit.clear();
        final long[] updateCounter = new long[] { 0L };
        delete = new Delete(toBytes("Key5"));
        delete.deleteColumns(family1, family1_column2);
        delete.deleteColumns(family2, family2_column1);
        delete.deleteFamily(family3);
        batchLsit.add(delete);
        for (int i = 0; i < rows; ++i) {
            Put put = new Put(toBytes("Key" + i));
            put.add(family1, family1_column1, family1_value);
            put.add(family1, family1_column2, family1_value);
            put.add(family1, family1_column3, family1_value);
            put.add(family2, family2_column1, family2_value);
            put.add(family2, family2_column2, family2_value);
            put.add(family3, family3_column1, family3_value);
            batchLsit.add(put);
        }
        hTable.batchCallback(batchLsit, new Batch.Callback<MutationResult>() {
            @Override
            public void update(byte[] region, byte[] row, MutationResult result) {
                updateCounter[0]++;
            }
        });
        assertEquals(11, updateCounter[0]);

    }

    @Test
    public void testMultiColumnFamilyPut() throws Exception {
        byte[] family1 = "family_with_group1".getBytes();
        byte[] family2 = "family_with_group2".getBytes();
        byte[] family3 = "family_with_group3".getBytes();

        byte[] family1_column1 = "family1_column1".getBytes();
        byte[] family1_column2 = "family1_column2".getBytes();
        byte[] family1_column3 = "family1_column3".getBytes();
        byte[] family2_column1 = "family2_column1".getBytes();
        byte[] family2_column2 = "family2_column2".getBytes();
        byte[] family3_column1 = "family3_column1".getBytes();
        byte[] family1_value = "VVV1".getBytes();
        byte[] family2_value = "VVV2".getBytes();
        byte[] family3_value = "VVV3".getBytes();

        Map<byte[], byte[]> expectedValues = new HashMap<>();
        expectedValues.put(family1_column1, family1_value);
        expectedValues.put(family1_column2, family1_value);
        expectedValues.put(family1_column3, family1_value);
        expectedValues.put(family2_column1, family2_value);
        expectedValues.put(family2_column2, family2_value);
        expectedValues.put(family3_column1, family3_value);

        int rows = 30;

        for (int i = 0; i < rows; ++i) {
            Put put = new Put(toBytes("Key" + i));
            put.addColumn(family1, family1_column1, family1_value);
            put.addColumn(family1, family1_column2, family1_value);
            put.addColumn(family1, family1_column3, family1_value);
            put.addColumn(family2, family2_column1, family2_value);
            put.addColumn(family2, family2_column2, family2_value);
            put.addColumn(family3, family3_column1, family3_value);
            hTable.put(put);
        }

        Scan scan = new Scan();
        scan.setStartRow(toBytes("Key"));
        scan.setStopRow(toBytes("Kf"));
        ResultScanner scanner = hTable.getScanner(scan);
        int count = 0;

        for (Result result : scanner) {
            Cell[] keyValues = result.rawCells();
            long timestamp = keyValues[0].getTimestamp();
            for (int i = 1; i < keyValues.length; ++i) {
                assertEquals(timestamp, keyValues[i].getTimestamp());
                byte[] qualifier = CellUtil.cloneQualifier(keyValues[i]);
                byte[] expectedValue = expectedValues.get(qualifier);
                if (expectedValue != null) {
                    assertEquals(expectedValue, CellUtil.cloneValue(keyValues[i]));
                }
            }
            count++;
        }
        assertEquals(count, rows);
    }

    @Ignore
    public void testMultiColumnFamilyAppend() throws Exception {
        byte[] family1 = "family_with_group1".getBytes();
        byte[] family2 = "family_with_group2".getBytes();
        byte[] family3 = "family_with_group3".getBytes();

        byte[] family1_column1 = "family1_column1".getBytes();
        byte[] family1_column2 = "family1_column2".getBytes();
        byte[] family1_column3 = "family1_column3".getBytes();
        byte[] family2_column1 = "family2_column1".getBytes();
        byte[] family2_column2 = "family2_column2".getBytes();
        byte[] family3_column1 = "family3_column1".getBytes();
        byte[] family1_value = "VVV1".getBytes();
        byte[] family2_value = "VVV2".getBytes();
        byte[] family3_value = "VVV3".getBytes();

        Map<byte[], byte[]> expectedValues = new HashMap<>();
        expectedValues.put(family1_column1, family1_value);
        expectedValues.put(family1_column2, family1_value);
        expectedValues.put(family1_column3, family1_value);
        expectedValues.put(family2_column1, family2_value);
        expectedValues.put(family2_column2, family2_value);
        expectedValues.put(family3_column1, family3_value);

        int rows = 30;

        for (int i = 0; i < rows; ++i) {
            Append append = new Append(toBytes("Key" + i));
            append.add(family1, family1_column1, family1_value);
            append.add(family1, family1_column2, family1_value);
            append.add(family1, family1_column3, family1_value);
            append.add(family2, family2_column1, family2_value);
            append.add(family2, family2_column2, family2_value);
            append.add(family3, family3_column1, family3_value);
            hTable.append(append);
        }

        Scan scan = new Scan();
        scan.setStartRow(toBytes("Key"));
        scan.setStopRow(toBytes("Kf"));
        ResultScanner scanner = hTable.getScanner(scan);
        int count = 0;

        for (Result result : scanner) {
            Cell[] keyValues = result.rawCells();
            long timestamp = keyValues[0].getTimestamp();
            for (int i = 1; i < keyValues.length; ++i) {
                assertEquals(timestamp, keyValues[i].getTimestamp());
                byte[] qualifier = CellUtil.cloneQualifier(keyValues[i]);
                byte[] expectedValue = expectedValues.get(qualifier);
                if (expectedValue != null) {
                    assertEquals(expectedValue, CellUtil.cloneValue(keyValues[i]));
                }
            }
            count++;
        }
        assertEquals(count, rows);
    }

    @Test
    public void testMultiColumnFamilyReverseScan() throws Exception {
        byte[] family1 = "family_with_group1".getBytes();
        byte[] family2 = "family_with_group2".getBytes();
        byte[] family3 = "family_with_group3".getBytes();

        byte[] family1_column1 = "family1_column1".getBytes();
        byte[] family1_column2 = "family1_column2".getBytes();
        byte[] family1_column3 = "family1_column3".getBytes();
        byte[] family2_column1 = "family2_column1".getBytes();
        byte[] family2_column2 = "family2_column2".getBytes();
        byte[] family3_column1 = "family3_column1".getBytes();
        byte[] family1_value = "VVV1".getBytes();
        byte[] family2_value = "VVV2".getBytes();
        byte[] family3_value = "VVV3".getBytes();

        Map<byte[], byte[]> expectedValues = new HashMap<>();
        expectedValues.put(family1_column1, family1_value);
        expectedValues.put(family1_column2, family1_value);
        expectedValues.put(family1_column3, family1_value);
        expectedValues.put(family2_column1, family2_value);
        expectedValues.put(family2_column2, family2_value);
        expectedValues.put(family3_column1, family3_value);

        int rows = 30;

        for (int i = 0; i < rows; ++i) {
            Put put = new Put(toBytes("Key" + i));
            put.addColumn(family1, family1_column1, family1_value);
            put.addColumn(family1, family1_column2, family1_value);
            put.addColumn(family1, family1_column3, family1_value);
            put.addColumn(family2, family2_column1, family2_value);
            put.addColumn(family2, family2_column2, family2_value);
            put.addColumn(family3, family3_column1, family3_value);
            hTable.put(put);
        }

        Scan scan = new Scan();
        scan.addFamily(family1);
        scan.addFamily(family2);
        scan.setReversed(true);
        ResultScanner scanner2 = hTable.getScanner(scan);

        for (Result result : scanner2) {
            Cell[] keyValues = result.rawCells();
            long timestamp = keyValues[0].getTimestamp();
            for (int i = 1; i < keyValues.length; ++i) {
                assertEquals(timestamp, keyValues[i].getTimestamp());
                byte[] qualifier = CellUtil.cloneQualifier(keyValues[i]);
                byte[] expectedValue = expectedValues.get(qualifier);
                if (expectedValue != null) {
                    assertEquals(expectedValue, CellUtil.cloneValue(keyValues[i]));
                }
            }
        }
    }

    @Test
    public void testMultiColumnFamilyScanWithColumns() throws Exception {
        byte[] family1 = "family_with_group1".getBytes();
        byte[] family2 = "family_with_group2".getBytes();
        byte[] family3 = "family_with_group3".getBytes();

        byte[] family1_column1 = "family1_column1".getBytes();
        byte[] family1_column2 = "family1_column2".getBytes();
        byte[] family1_column3 = "family1_column3".getBytes();
        byte[] family2_column1 = "family2_column1".getBytes();
        byte[] family2_column2 = "family2_column2".getBytes();
        byte[] family3_column1 = "family3_column1".getBytes();
        byte[] family1_value = "VVV1".getBytes();
        byte[] family2_value = "VVV2".getBytes();
        byte[] family3_value = "VVV3".getBytes();

        Map<byte[], byte[]> expectedValues = new HashMap<>();
        expectedValues.put(family1_column1, family1_value);
        expectedValues.put(family1_column2, family1_value);
        expectedValues.put(family1_column3, family1_value);
        expectedValues.put(family2_column1, family2_value);
        expectedValues.put(family2_column2, family2_value);
        expectedValues.put(family3_column1, family3_value);

        int rows = 30;

        for (int i = 0; i < rows; ++i) {
            Put put = new Put(toBytes("Key" + i));
            put.addColumn(family1, family1_column1, family1_value);
            put.addColumn(family1, family1_column2, family1_value);
            put.addColumn(family1, family1_column3, family1_value);
            put.addColumn(family2, family2_column1, family2_value);
            put.addColumn(family2, family2_column2, family2_value);
            put.addColumn(family3, family3_column1, family3_value);
            hTable.put(put);
        }

        Scan scan = new Scan();
        scan.setStartRow(toBytes("Key"));
        scan.setStopRow(toBytes("Kf"));
        scan.addColumn(family1, family1_column1);
        scan.addColumn(family2, family2_column1);
        ResultScanner scanner = hTable.getScanner(scan);

        for (Result result : scanner) {
            Cell[] keyValues = result.rawCells();
            long timestamp = keyValues[0].getTimestamp();
            for (int i = 1; i < keyValues.length; ++i) {
                assertEquals(timestamp, keyValues[i].getTimestamp());
                byte[] qualifier = CellUtil.cloneQualifier(keyValues[i]);
                byte[] expectedValue = expectedValues.get(qualifier);
                if (expectedValue != null) {
                    assertEquals(expectedValue, CellUtil.cloneValue(keyValues[i]));
                }
            }
            assertEquals(2, keyValues.length);
        }
        scanner.close();
        scan = new Scan();
        scan.setStartRow(toBytes("Key"));
        scan.setStopRow(toBytes("Kf"));
        scan.addColumn(family1, family1_column1);
        scan.addColumn(family1, family1_column2);
        scan.addColumn(family1, family1_column3);
        scan.addColumn(family2, family2_column1);
        scan.addColumn(family2, family2_column2);
        scanner = hTable.getScanner(scan);

        for (Result result : scanner) {
            Cell[] keyValues = result.rawCells();
            long timestamp = keyValues[0].getTimestamp();
            for (int i = 1; i < keyValues.length; ++i) {
                assertEquals(timestamp, keyValues[i].getTimestamp());
                byte[] qualifier = CellUtil.cloneQualifier(keyValues[i]);
                byte[] expectedValue = expectedValues.get(qualifier);
                if (expectedValue != null) {
                    assertEquals(expectedValue, CellUtil.cloneValue(keyValues[i]));
                }
            }
            assertEquals(5, keyValues.length);
        }
        scanner.close();
        scan = new Scan();
        scan.setStartRow(toBytes("Key"));
        scan.setStopRow(toBytes("Kf"));
        scan.addFamily(family1);
        scan.addFamily(family2);

        scanner = hTable.getScanner(scan);

        for (Result result : scanner) {
            Cell[] keyValues = result.rawCells();
            long timestamp = keyValues[0].getTimestamp();
            for (int i = 1; i < keyValues.length; ++i) {
                assertEquals(timestamp, keyValues[i].getTimestamp());
                byte[] qualifier = CellUtil.cloneQualifier(keyValues[i]);
                byte[] expectedValue = expectedValues.get(qualifier);
                if (expectedValue != null) {
                    assertEquals(expectedValue, CellUtil.cloneValue(keyValues[i]));
                }
            }
            assertEquals(5, keyValues.length);
        }
        scanner.close();
        scan = new Scan();
        scan.setStartRow(toBytes("Key"));
        scan.setStopRow(toBytes("Kf"));
        scan.addFamily(family1);
        scan.addFamily(family3);

        scanner = hTable.getScanner(scan);

        for (Result result : scanner) {
            Cell[] keyValues = result.rawCells();
            long timestamp = keyValues[0].getTimestamp();
            for (int i = 1; i < keyValues.length; ++i) {
                assertEquals(timestamp, keyValues[i].getTimestamp());
                byte[] qualifier = CellUtil.cloneQualifier(keyValues[i]);
                byte[] expectedValue = expectedValues.get(qualifier);
                if (expectedValue != null) {
                    assertEquals(expectedValue, CellUtil.cloneValue(keyValues[i]));
                }
            }
            // f1c1 f1c2 f1c3 f3c1
            assertEquals(4, keyValues.length);
        }
        scanner.close();
    }

    @Test
    public void testMultiColumnFamilyScanWithFilter() throws Exception {
        byte[] family1 = "family_with_group1".getBytes();
        byte[] family2 = "family_with_group2".getBytes();
        byte[] family3 = "family_with_group3".getBytes();

        byte[] family1_column1 = "family1_column1".getBytes();
        byte[] family1_column2 = "family1_column2".getBytes();
        byte[] family1_column3 = "family1_column3".getBytes();
        byte[] family2_column1 = "family2_column1".getBytes();
        byte[] family2_column2 = "family2_column2".getBytes();
        byte[] family3_column1 = "family3_column1".getBytes();
        byte[] family1_value = "VVV1".getBytes();
        byte[] family2_value = "VVV2".getBytes();
        byte[] family3_value = "VVV3".getBytes();

        Map<byte[], byte[]> expectedValues = new HashMap<>();
        expectedValues.put(family1_column1, family1_value);
        expectedValues.put(family1_column2, family1_value);
        expectedValues.put(family1_column3, family1_value);
        expectedValues.put(family2_column1, family2_value);
        expectedValues.put(family2_column2, family2_value);
        expectedValues.put(family3_column1, family3_value);

        int rows = 30;

        for (int i = 0; i < rows; ++i) {
            Put put = new Put(toBytes("Key" + i));
            put.addColumn(family1, family1_column1, family1_value);
            put.addColumn(family1, family1_column2, family1_value);
            put.addColumn(family1, family1_column3, family1_value);
            put.addColumn(family2, family2_column1, family2_value);
            put.addColumn(family2, family2_column2, family2_value);
            put.addColumn(family3, family3_column1, family3_value);
            hTable.put(put);
        }

        PrefixFilter filter = new PrefixFilter(toBytes("Key1"));
        Scan scan = new Scan();
        scan.setStartRow(toBytes("Key"));
        scan.setStopRow(toBytes("Kf"));
        scan.setFilter(filter);
        ResultScanner scanner = hTable.getScanner(scan);

        // Key1, Key10, Key11, Key12, Key13, Key14, Key15, Key16, Key17, Key18, Key19
        int count = 0;
        for (Result result : scanner) {
            Cell[] keyValues = result.rawCells();
            long timestamp = keyValues[0].getTimestamp();
            for (int i = 1; i < keyValues.length; ++i) {
                assertEquals(timestamp, keyValues[i].getTimestamp());
                byte[] qualifier = CellUtil.cloneQualifier(keyValues[i]);
                byte[] expectedValue = expectedValues.get(qualifier);
                if (expectedValue != null) {
                    assertEquals(expectedValue, CellUtil.cloneValue(keyValues[i]));
                }
            }
            assertEquals(6, keyValues.length);
            count++;
        }
        assertEquals(11, count);
    }

    @Test
    public void testMultiColumnFamilyGet() throws Exception {
        byte[] family1 = "family_with_group1".getBytes();
        byte[] family2 = "family_with_group2".getBytes();
        byte[] family3 = "family_with_group3".getBytes();

        byte[] family1_column1 = "family1_column1".getBytes();
        byte[] family1_column2 = "family1_column2".getBytes();
        byte[] family1_column3 = "family1_column3".getBytes();
        byte[] family2_column1 = "family2_column1".getBytes();
        byte[] family2_column2 = "family2_column2".getBytes();
        byte[] family3_column1 = "family3_column1".getBytes();
        byte[] family1_value = "VVV1".getBytes();
        byte[] family2_value = "VVV2".getBytes();
        byte[] family3_value = "VVV3".getBytes();

        Map<byte[], byte[]> expectedValues = new HashMap<>();
        expectedValues.put(family1_column1, family1_value);
        expectedValues.put(family1_column2, family1_value);
        expectedValues.put(family1_column3, family1_value);
        expectedValues.put(family2_column1, family2_value);
        expectedValues.put(family2_column2, family2_value);
        expectedValues.put(family3_column1, family3_value);

        int rows = 3;

        for (int i = 0; i < rows; ++i) {
            Put put = new Put(toBytes("Key" + i));
            put.addColumn(family1, family1_column1, family1_value);
            put.addColumn(family1, family1_column2, family1_value);
            put.addColumn(family1, family1_column3, family1_value);
            put.addColumn(family2, family2_column1, family2_value);
            put.addColumn(family2, family2_column2, family2_value);
            put.addColumn(family3, family3_column1, family3_value);
            hTable.put(put);
        }

        // get with empty family
        // f1c1 f1c2 f1c3 f2c1 f2c2 f3c1
        Get get = new Get(toBytes("Key1"));
        Result result = hTable.get(get);
        Cell[] keyValues = result.rawCells();
        long timestamp = keyValues[0].getTimestamp();
        for (int i = 1; i < keyValues.length; ++i) {
            assertEquals(timestamp, keyValues[i].getTimestamp());
            byte[] qualifier = CellUtil.cloneQualifier(keyValues[i]);
            byte[] expectedValue = expectedValues.get(qualifier);
            if (expectedValue != null) {
                assertEquals(expectedValue, CellUtil.cloneValue(keyValues[i]));
            }
        }
        assertEquals(6, keyValues.length);

        // f1c1 f2c1 f2c2
        Get get2 = new Get(toBytes("Key1"));
        get2.addColumn(family1, family1_column1);
        get2.addColumn(family2, family2_column1);
        get2.addColumn(family2, family2_column2);
        Result result2 = hTable.get(get2);
        keyValues = result2.rawCells();
        timestamp = keyValues[0].getTimestamp();
        for (int i = 1; i < keyValues.length; ++i) {
            assertEquals(timestamp, keyValues[i].getTimestamp());
            byte[] qualifier = CellUtil.cloneQualifier(keyValues[i]);
            byte[] expectedValue = expectedValues.get(qualifier);
            if (expectedValue != null) {
                assertEquals(expectedValue, CellUtil.cloneValue(keyValues[i]));
            }
        }
        assertEquals(3, keyValues.length);

        //f2c1 f2c2
        Get get3 = new Get(toBytes("Key1"));
        get3.addFamily(family1);
        get3.addColumn(family2, family2_column1);
        get3.addColumn(family2, family2_column2);
        Result result3 = hTable.get(get3);
        keyValues = result3.rawCells();
        timestamp = keyValues[0].getTimestamp();
        for (int i = 1; i < keyValues.length; ++i) {
            assertEquals(timestamp, keyValues[i].getTimestamp());
            byte[] qualifier = CellUtil.cloneQualifier(keyValues[i]);
            byte[] expectedValue = expectedValues.get(qualifier);
            if (expectedValue != null) {
                assertEquals(expectedValue, CellUtil.cloneValue(keyValues[i]));
            }
        }
        assertEquals(5, keyValues.length);
    }

    @Test
    public void testMultiColumnFamilyDelete() throws Exception {
        byte[] family1 = "family_with_group1".getBytes();
        byte[] family2 = "family_with_group2".getBytes();
        byte[] family3 = "family_with_group3".getBytes();

        byte[] family1_column1 = "family1_column1".getBytes();
        byte[] family1_column2 = "family1_column2".getBytes();
        byte[] family1_column3 = "family1_column3".getBytes();
        byte[] family2_column1 = "family2_column1".getBytes();
        byte[] family2_column2 = "family2_column2".getBytes();
        byte[] family3_column1 = "family3_column1".getBytes();
        byte[] family1_value = "VVV1".getBytes();
        byte[] family2_value = "VVV2".getBytes();
        byte[] family3_value = "VVV3".getBytes();

        int rows = 10;

        for (int i = 0; i < rows; ++i) {
            Put put = new Put(toBytes("Key" + i));
            Delete delete = new Delete(toBytes("Key" + i));
            hTable.delete(delete);
            put.addColumn(family1, family1_column1, family1_value);
            put.addColumn(family1, family1_column2, family1_value);
            put.addColumn(family1, family1_column3, family1_value);
            put.addColumn(family2, family2_column1, family2_value);
            put.addColumn(family2, family2_column2, family2_value);
            put.addColumn(family3, family3_column1, family3_value);
            hTable.put(put);
        }

        // f1c1 f1c2 f1c3 f2c1 f2c2 f3c1
        Delete delete = new Delete(toBytes("Key1"));
        delete.addColumns(family1, family1_column1);
        delete.addColumns(family2, family2_column1);
        hTable.delete(delete);
        // f1c2 f1c3 f2c2 f3c1
        Get get = new Get(toBytes("Key1"));
        Result result = hTable.get(get);
        Cell[] keyValues = result.rawCells();
        assertEquals(4, keyValues.length);
        assertFalse(result.containsColumn(family1, family1_column1));
        assertFalse(result.containsColumn(family2, family2_column1));

        assertTrue(result.containsColumn(family1, family1_column2));
        assertArrayEquals(result.getValue(family1, family1_column2), family1_value);
        assertTrue(result.containsColumn(family1, family1_column3));
        assertArrayEquals(result.getValue(family1, family1_column3), family1_value);
        assertTrue(result.containsColumn(family2, family2_column2));
        assertArrayEquals(result.getValue(family2, family2_column2), family2_value);
        assertTrue(result.containsColumn(family3, family3_column1));
        assertArrayEquals(result.getValue(family3, family3_column1), family3_value);

        // f1c1 f1c2 f1c3 f2c1 f2c2 f3c1
        delete = new Delete(toBytes("Key2"));
        delete.addFamily(family1);
        delete.addFamily(family2);
        // f3c1
        hTable.delete(delete);
        get = new Get(toBytes("Key2"));
        result = hTable.get(get);
        keyValues = result.rawCells();
        assertEquals(1, keyValues.length);

        // f1c1 f1c2 f1c3 f2c1 f2c2 f3c1
        delete = new Delete(toBytes("Key3"));
        delete.addFamily(family1);
        delete.addColumns(family2, family2_column1);
        hTable.delete(delete);
        // f2c2 f3c1
        get = new Get(toBytes("Key3"));
        result = hTable.get(get);
        keyValues = result.rawCells();
        assertEquals(2, keyValues.length);

        // f1c1 f1c2 f1c3 f2c1 f2c2 f3c1
        delete = new Delete(toBytes("Key4"));
        hTable.delete(delete);
        // null
        get = new Get(toBytes("Key4"));
        result = hTable.get(get);
        keyValues = result.rawCells();
        assertEquals(0, keyValues.length);

        // f1c1 f2c1 f2c2
        delete = new Delete(toBytes("Key5"));
        delete.addColumns(family1, family1_column2);
        delete.addColumns(family1, family1_column3);
        delete.addColumns(family3, family3_column1);
        hTable.delete(delete);
        // null
        get = new Get(toBytes("Key5"));
        result = hTable.get(get);
        keyValues = result.rawCells();
        assertEquals(3, keyValues.length);

        for (int i = 0; i < rows; ++i) {
            Put put = new Put(toBytes("Key" + i));
            put.addColumn(family1, family1_column1, family1_value);
            put.addColumn(family1, family1_column2, family1_value);
            put.addColumn(family1, family1_column3, family1_value);
            put.addColumn(family2, family2_column1, family2_value);
            put.addColumn(family2, family2_column2, family2_value);
            put.addColumn(family3, family3_column1, family3_value);
            hTable.put(put);
        }

        delete = new Delete(toBytes("Key6"));
        delete.addColumn(family1, family1_column2);
        delete.addColumn(family2, family2_column1);
        hTable.delete(delete);
        get = new Get(toBytes("Key6"));
        result = hTable.get(get);
        keyValues = result.rawCells();
        assertEquals(6, keyValues.length);

        long lastTimestamp = result.getColumnCells(family1, family1_column1).get(0).getTimestamp();
        assertEquals(lastTimestamp, result.getColumnCells(family1, family1_column3).get(0)
            .getTimestamp());
        assertEquals(lastTimestamp, result.getColumnCells(family2, family2_column2).get(0)
            .getTimestamp());
        assertEquals(lastTimestamp, result.getColumnCells(family3, family3_column1).get(0)
            .getTimestamp());

        long oldTimestamp = result.getColumnCells(family1, family1_column2).get(0).getTimestamp();
        assertEquals(oldTimestamp, result.getColumnCells(family2, family2_column1).get(0)
            .getTimestamp());
        assertTrue(lastTimestamp > oldTimestamp);
    }
}
