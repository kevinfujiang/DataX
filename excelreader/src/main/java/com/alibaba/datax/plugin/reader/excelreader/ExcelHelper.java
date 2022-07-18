/*
 *
 *  * Licensed to the Apache Software Foundation (ASF) under one
 *  * or more contributor license agreements.  See the NOTICE file
 *  * distributed with this work for additional information
 *  * regarding copyright ownership.  The ASF licenses this file
 *  * to you under the Apache License, Version 2.0 (the
 *  * "License"); you may not use this file except in compliance
 *  * with the License.  You may obtain a copy of the License at
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing,
 *  * software distributed under the License is distributed on an
 *  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  * KIND, either express or implied.  See the License for the
 *  * specific language governing permissions and limitations
 *  * under the License.
 *
 */

package com.alibaba.datax.plugin.reader.excelreader;

import com.alibaba.datax.common.element.BoolColumn;
import com.alibaba.datax.common.element.DateColumn;
import com.alibaba.datax.common.element.DoubleColumn;
import com.alibaba.datax.common.element.LongColumn;
import com.alibaba.datax.common.element.Record;
import com.alibaba.datax.common.element.StringColumn;
import com.alibaba.datax.common.exception.DataXException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class ExcelHelper
{
    private static final Logger LOG = LoggerFactory.getLogger(ExcelHelper.class);

    public boolean header;
    public int skipRows;
    public List<Integer> ignoreColumns;

    FileInputStream file;
    Workbook workbook;
    private FormulaEvaluator evaluator;
    private Iterator<Row> rowIterator;

    public ExcelHelper(boolean header, int skipRows){
        this.header = header;
        this.skipRows = skipRows;
    }

    public ExcelHelper(boolean header, int skipRows, List<Integer> ignoreColumns){
        this.header = header;
        this.skipRows = skipRows;
        this.ignoreColumns = ignoreColumns;
    }

    public void open(String filePath)
    {
        try {
            this.file = new FileInputStream(filePath);
            if (filePath.endsWith(".xlsx")) {
                this.workbook = new XSSFWorkbook(file);
            } else {
                this.workbook = new HSSFWorkbook(file);
            }
            // ONLY reader the first sheet
            Sheet sheet = workbook.getSheetAt(0);
            this.evaluator =  workbook.getCreationHelper().createFormulaEvaluator();
            this.rowIterator = sheet.iterator();
            if (this.header && this.rowIterator.hasNext()) {
                // skip header
                this.rowIterator.next();
            }
            if (this.skipRows > 0) {
                int i =0;
                while (this.rowIterator.hasNext() && i < this.skipRows) {
                    this.rowIterator.next();
                    i++;
                }
            }
        }
        catch (FileNotFoundException e) {
            throw DataXException.asDataXException(ExcelReaderErrorCode.OPEN_FILE_ERROR, e);
        }
        catch (IOException e) {
            throw DataXException.asDataXException(ExcelReaderErrorCode.OPEN_FILE_ERROR,
                    "IOException occurred when open '" + filePath + "':" + e.getMessage());
        }
    }

    public void close()
    {
        try {
            this.workbook.close();
            this.file.close();
        }
        catch (IOException ignored) {

        }
    }

    public Record readLine(Record record)
    {
        if (rowIterator.hasNext()) {
            Row row = rowIterator.next();
            int columnIndex = 0;
            //For each row, iterate through all the columns
            Iterator<Cell> cellIterator = row.cellIterator();
            while (cellIterator.hasNext()) {
                int currentColumnIndex = columnIndex++;
                Cell cell = cellIterator.next();
                if (ignoreColumns != null &&  ignoreColumns.contains(currentColumnIndex)){
                    LOG.info("ignore column: {}", currentColumnIndex);
                    continue;
                }
                //Check the cell type after evaluating formulae
                //If it is formula cell, it will be evaluated otherwise no change will happen
                switch (evaluator.evaluateInCell(cell).getCellType()) {
                    case NUMERIC:
                        // numeric include whole numbers, fractional numbers, dates
                        if (DateUtil.isCellDateFormatted(cell)) {
                            record.addColumn(new DateColumn(cell.getDateCellValue()));
                        } else {
                            // integer or long ?
                            double a = cell.getNumericCellValue();
                            if ((long) a == a) {
                                record.addColumn(new LongColumn((long) a));
                            } else {
                                record.addColumn(new DoubleColumn(a));
                            }
                        }
                        break;
                    case STRING:
                        record.addColumn(new StringColumn(cell.getStringCellValue().trim()));
                        break;
                    case BOOLEAN:
                        record.addColumn(new BoolColumn(cell.getBooleanCellValue()));
                        break;
                    case FORMULA:
                    case _NONE:
                        break;
                    case ERROR:
                        // #VALUE!
                        record.addColumn(new StringColumn());
                        break;
                    case BLANK:
                        // empty cell
                        record.addColumn(new StringColumn(""));
                        break;
                }
            }
            return record;
        }
        else {
            return null;
        }
    }
}
