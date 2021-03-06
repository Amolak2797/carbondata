/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.carbondata.sdk.file;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;

import org.apache.carbondata.common.exceptions.sql.InvalidLoadOptionException;
import org.apache.carbondata.core.constants.CarbonCommonConstants;
import org.apache.carbondata.core.datastore.impl.FileFactory;
import org.apache.carbondata.core.util.CarbonProperties;
import org.apache.carbondata.core.util.path.CarbonTablePath;

import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.JsonDecoder;
import org.junit.Assert;

public class TestUtil {

  public static GenericData.Record jsonToAvro(String json, String avroSchema) throws IOException {
    InputStream input = null;
    DataFileWriter writer = null;
    Encoder encoder = null;
    ByteArrayOutputStream output = null;
    try {
      org.apache.avro.Schema schema = new org.apache.avro.Schema.Parser().parse(avroSchema);
      GenericDatumReader reader = new GenericDatumReader (schema);
      input = new ByteArrayInputStream(json.getBytes());
      output = new ByteArrayOutputStream();
      DataInputStream din = new DataInputStream(input);
      writer = new DataFileWriter (new GenericDatumWriter ());
      writer.create(schema, output);
      JsonDecoder decoder = DecoderFactory.get().jsonDecoder(schema, din);
      GenericData.Record datum = null;
      datum = (GenericData.Record) reader.read(null, decoder);
      return datum;
    } finally {
      try {
        input.close();
        writer.close();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  static void writeFilesAndVerify(Schema schema, String path) {
    writeFilesAndVerify(schema, path, null);
  }

  static void writeFilesAndVerify(Schema schema, String path, String[] sortColumns) {
    writeFilesAndVerify(100, schema, path, sortColumns, false, -1, -1, true);
  }

  public static void writeFilesAndVerify(int rows, Schema schema, String path, boolean persistSchema) {
    writeFilesAndVerify(rows, schema, path, null, persistSchema, -1, -1, true);
  }

  public static void writeFilesAndVerify(Schema schema, String path, boolean persistSchema,
      boolean isTransactionalTable) {
    writeFilesAndVerify(100, schema, path, null, persistSchema, -1, -1, isTransactionalTable);
  }

  /**
   * write file and verify
   *
   * @param rows                 number of rows
   * @param schema               schema
   * @param path                 table store path
   * @param persistSchema        whether persist schema
   * @param isTransactionalTable whether is transactional table
   */
  public static void writeFilesAndVerify(int rows, Schema schema, String path, boolean persistSchema,
    boolean isTransactionalTable) {
    writeFilesAndVerify(rows, schema, path, null, persistSchema, -1, -1, isTransactionalTable);
  }

  /**
   * Invoke CarbonWriter API to write carbon files and assert the file is rewritten
   * @param rows number of rows to write
   * @param schema schema of the file
   * @param path local write path
   * @param sortColumns sort columns
   * @param persistSchema true if want to persist schema file
   * @param blockletSize blockletSize in the file, -1 for default size
   * @param blockSize blockSize in the file, -1 for default size
   * @param isTransactionalTable set to true if this is written for Transactional Table.
   */
  public static void writeFilesAndVerify(int rows, Schema schema, String path, String[] sortColumns,
      boolean persistSchema, int blockletSize, int blockSize, boolean isTransactionalTable) {
    try {
      CarbonWriterBuilder builder = CarbonWriter.builder()
          .isTransactionalTable(isTransactionalTable)
          .outputPath(path);
      if (sortColumns != null) {
        builder = builder.sortBy(sortColumns);
      }
      if (persistSchema) {
        builder = builder.persistSchemaFile(true);
      }
      if (blockletSize != -1) {
        builder = builder.withBlockletSize(blockletSize);
      }
      if (blockSize != -1) {
        builder = builder.withBlockSize(blockSize);
      }

      CarbonWriter writer = builder.buildWriterForCSVInput(schema);

      for (int i = 0; i < rows; i++) {
        writer.write(new String[]{"robot" + (i % 10), String.valueOf(i), String.valueOf((double) i / 2)});
      }
      writer.close();
    } catch (IOException e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    } catch (InvalidLoadOptionException l) {
      l.printStackTrace();
      Assert.fail(l.getMessage());
    }

    File segmentFolder = null;
    if (isTransactionalTable) {
      segmentFolder = new File(CarbonTablePath.getSegmentPath(path, "null"));
      Assert.assertTrue(segmentFolder.exists());
    } else {
      segmentFolder = new File(path);
      Assert.assertTrue(segmentFolder.exists());
    }

    File[] dataFiles = segmentFolder.listFiles(new FileFilter() {
      @Override public boolean accept(File pathname) {
        return pathname.getName().endsWith(CarbonCommonConstants.FACT_FILE_EXT);
      }
    });
    Assert.assertNotNull(dataFiles);
    Assert.assertTrue(dataFiles.length > 0);
  }

  /**
   * verify whether the file exists
   * if delete the file success or file not exists, then return true; otherwise return false
   *
   * @return boolean
   */
  public static boolean cleanMdtFile() {
    String fileName = CarbonProperties.getInstance().getSystemFolderLocation()
            + CarbonCommonConstants.FILE_SEPARATOR + "datamap.mdtfile";
    try {
      if (FileFactory.isFileExist(fileName)) {
        File file = new File(fileName);
        file.delete();
        return true;
      } else {
        return true;
      }
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
  }

  /**
   * verify whether the mdt file exists
   * if the file exists, then return true; otherwise return false
   *
   * @return boolean
   */
  public static boolean verifyMdtFile() {
    String fileName = CarbonProperties.getInstance().getSystemFolderLocation()
            + CarbonCommonConstants.FILE_SEPARATOR + "datamap.mdtfile";
    try {
      if (FileFactory.isFileExist(fileName)) {
        return true;
      }
      return false;
    } catch (IOException e) {
      throw new RuntimeException("IO exception:", e);
    }
  }
}
