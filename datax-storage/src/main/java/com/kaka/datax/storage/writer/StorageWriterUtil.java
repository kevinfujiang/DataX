
package com.kaka.datax.storage.writer;

import com.alibaba.datax.common.base.Constant;
import com.alibaba.datax.common.base.Key;
import com.alibaba.datax.common.compress.ZipCycleOutputStream;
import com.alibaba.datax.common.element.Column;
import com.alibaba.datax.common.element.DateColumn;
import com.alibaba.datax.common.element.Record;
import com.alibaba.datax.common.exception.DataXException;
import com.alibaba.datax.common.plugin.RecordReceiver;
import com.alibaba.datax.common.plugin.TaskPluginCollector;
import com.alibaba.datax.common.util.Configuration;
import com.kaka.datax.storage.reader.StorageReaderErrorCode;
import com.kaka.datax.storage.util.FileHelper;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorOutputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StorageWriterUtil
{
    private static final Logger LOG = LoggerFactory.getLogger(StorageWriterUtil.class);
    private static final Set<String> supportedWriteModes = new HashSet<>(Arrays.asList("truncate", "append", "nonConflict", "overwrite"));

    private StorageWriterUtil()
    {

    }

    /*
     * check parameter: writeMode, encoding, compress, filedDelimiter
     */
    public static void validateParameter(Configuration writerConfiguration)
    {
        // writeMode check
        String writeMode = writerConfiguration.getNecessaryValue(Key.WRITE_MODE, StorageWriterErrorCode.REQUIRED_VALUE);
        writeMode = writeMode.trim();
        if (!supportedWriteModes.contains(writeMode)) {
            throw DataXException.asDataXException(
                            StorageWriterErrorCode.ILLEGAL_VALUE,
                            String.format(
                                    "'%s' is unsupported, supported write mode: [%s]",
                                    writeMode, StringUtils.join(supportedWriteModes, ",")));
        }
        writerConfiguration.set(Key.WRITE_MODE, writeMode);

        // encoding check
        String encoding = writerConfiguration.getString(Key.ENCODING);
        if (StringUtils.isBlank(encoding)) {
            // like "  ", null
            LOG.warn(String.format("您的encoding配置为空, 将使用默认值[%s]", Constant.DEFAULT_ENCODING));
            writerConfiguration.set(Key.ENCODING, Constant.DEFAULT_ENCODING);
        }
        else {
            try {
                encoding = encoding.trim();
                writerConfiguration.set(Key.ENCODING, encoding);
                Charsets.toCharset(encoding);
            }
            catch (Exception e) {
                throw DataXException.asDataXException(
                        StorageWriterErrorCode.ILLEGAL_VALUE,
                        String.format("不支持您配置的编码格式:[%s]", encoding), e);
            }
        }

        // only support compress types
        String compress = writerConfiguration.getString(Key.COMPRESS);
        if (StringUtils.isBlank(compress)) {
            writerConfiguration.set(Key.COMPRESS, null);
        }

        // fieldDelimiter check
        String delimiterInStr = writerConfiguration.getString(Key.FIELD_DELIMITER);
        // warn: if it has, length must be one
        if (null != delimiterInStr && 1 != delimiterInStr.length()) {
            throw DataXException.asDataXException(
                    StorageWriterErrorCode.ILLEGAL_VALUE,
                    String.format("仅仅支持单字符切分, 您配置的切分为 : [%s]", delimiterInStr));
        }
        if (null == delimiterInStr) {
            LOG.warn(String.format("您没有配置列分隔符, 使用默认值[%s]", Constant.DEFAULT_FIELD_DELIMITER));
            writerConfiguration.set(Key.FIELD_DELIMITER, Constant.DEFAULT_FIELD_DELIMITER);
        }

        // fileFormat check
        String fileFormat = writerConfiguration.getString(Key.FILE_FORMAT, Constant.DEFAULT_FILE_FORMAT);
        if (!Constant.SUPPORTED_FILE_FORMAT.contains(fileFormat)) {
            throw DataXException.asDataXException(
                    StorageWriterErrorCode.ILLEGAL_VALUE,
                    String.format("您配置的fileFormat [%s]错误, 支持[%s]两种.", fileFormat, Constant.SUPPORTED_FILE_FORMAT));
        }
    }

    public static List<Configuration> split(Configuration writerSliceConfig, Set<String> originAllFileExists, int mandatoryNumber)
    {
        LOG.info("begin do split...");
        if (mandatoryNumber == 1) {
            return Collections.singletonList(writerSliceConfig);
        }

        Set<String> allFileExists = new HashSet<>(originAllFileExists);
        List<Configuration> writerSplitConfigs = new ArrayList<>();
        String filePrefix = writerSliceConfig.getString(Key.FILE_NAME);

        for (int i = 0; i < mandatoryNumber; i++) {
            // handle same file name
            Configuration splitTaskConfig = writerSliceConfig.clone();
            String fullFileName;
            fullFileName = String.format("%s__%s", filePrefix, FileHelper.generateFileMiddleName());
            while (allFileExists.contains(fullFileName)) {
                fullFileName = String.format("%s__%s", filePrefix, FileHelper.generateFileMiddleName());
            }
            allFileExists.add(fullFileName);
            splitTaskConfig.set(Key.FILE_NAME, fullFileName);
            LOG.info(String.format("split write file name:[%s]", fullFileName));
            writerSplitConfigs.add(splitTaskConfig);
        }
        LOG.info("end do split.");
        return writerSplitConfigs;
    }

    public static String buildFilePath(String path, String fileName, String suffix)
    {
        boolean isEndWithSeparator = false;
        switch (IOUtils.DIR_SEPARATOR) {
            case IOUtils.DIR_SEPARATOR_UNIX:
                isEndWithSeparator = path.endsWith(String.valueOf(IOUtils.DIR_SEPARATOR));
                break;
            case IOUtils.DIR_SEPARATOR_WINDOWS:
                isEndWithSeparator = path.endsWith(String.valueOf(IOUtils.DIR_SEPARATOR_WINDOWS));
                break;
            default:
                break;
        }
        if (!isEndWithSeparator) {
            path = path + IOUtils.DIR_SEPARATOR;
        }
        if (null == suffix) {
            suffix = "";
        }
        else {
            suffix = suffix.trim();
        }
        return String.format("%s%s%s", path, fileName, suffix);
    }

    public static void writeToStream(RecordReceiver lineReceiver,
            OutputStream outputStream, Configuration config, String fileName,
            TaskPluginCollector taskPluginCollector)
    {
        String encoding = config.getString(Key.ENCODING, Constant.DEFAULT_ENCODING);
        // handle blank encoding
        if (StringUtils.isBlank(encoding)) {
            LOG.warn("您配置的encoding为[{}], 使用默认值[{}]", encoding, Constant.DEFAULT_ENCODING);
            encoding = Constant.DEFAULT_ENCODING;
        }
        String compress = config.getString(Key.COMPRESS);

        BufferedWriter writer = null;
        // compress logic
        try {
            if (null == compress) {
                writer = new BufferedWriter(new OutputStreamWriter(outputStream, encoding));
            }
            else {
                //normalize compress name
                if ("gzip".equalsIgnoreCase(compress)) {
                    compress = "gz";
                }
                else if ("bz2".equalsIgnoreCase(compress)) {
                    compress = "bzip2";
                }

                if ("zip".equals(compress)) {
                    ZipCycleOutputStream zis = new ZipCycleOutputStream(outputStream, fileName);
                    writer = new BufferedWriter(new OutputStreamWriter(zis, encoding));
                }
                else {
                    CompressorOutputStream compressorOutputStream = new CompressorStreamFactory().createCompressorOutputStream(compress,
                            outputStream);
                    writer = new BufferedWriter(new OutputStreamWriter(compressorOutputStream, encoding));
                }
            }
            StorageWriterUtil.doWriteToStream(lineReceiver, writer, fileName, config, taskPluginCollector);
        }
        catch (UnsupportedEncodingException uee) {
            throw DataXException.asDataXException(
                            StorageWriterErrorCode.WRITE_FILE_WITH_CHARSET_ERROR,
                            String.format("不支持的编码格式 : [%s]", encoding), uee);
        }
        catch (NullPointerException e) {
            throw DataXException.asDataXException(
                    StorageWriterErrorCode.RUNTIME_EXCEPTION,
                    "运行时错误, 请联系我们", e);
        }
        catch (CompressorException e) {
            throw DataXException.asDataXException(
                    StorageReaderErrorCode.ILLEGAL_VALUE,
                    "The compress algorithm '" + compress + "' is unsupported yet"
            );
        }
        catch (IOException e) {
            throw DataXException.asDataXException(
                    StorageWriterErrorCode.WRITE_FILE_IO_ERROR,
                    String.format("流写入错误 : [%s]", fileName), e);
        }
        finally {
            IOUtils.closeQuietly(writer, null);
        }
    }

    private static void doWriteToStream(RecordReceiver lineReceiver,
            BufferedWriter writer, String context, Configuration config,
            TaskPluginCollector taskPluginCollector)
            throws IOException
    {
        CSVFormat.Builder csvBuilder = CSVFormat.DEFAULT.builder();
        csvBuilder.setRecordSeparator(IOUtils.LINE_SEPARATOR_UNIX);
        String nullFormat = config.getString(Key.NULL_FORMAT);
        csvBuilder.setNullString(nullFormat);
        // 兼容format & dataFormat
        String dateFormat = config.getString(Key.DATE_FORMAT);
        DateFormat dateParse = null; // warn: 可能不兼容
        if (StringUtils.isNotBlank(dateFormat)) {
            dateParse = new SimpleDateFormat(dateFormat);
        }

        // warn: default false
        String fileFormat = config.getString(Key.FILE_FORMAT, Constant.DEFAULT_FILE_FORMAT);

        String delimiterInStr = config.getString(Key.FIELD_DELIMITER);
        if (null != delimiterInStr && 1 != delimiterInStr.length()) {
            throw DataXException.asDataXException(
                    StorageWriterErrorCode.ILLEGAL_VALUE,
                    String.format("仅仅支持单字符切分, 您配置的切分为 : [%s]", delimiterInStr));
        }
        if (null == delimiterInStr) {
            LOG.warn(String.format("您没有配置列分隔符, 使用默认值[%s]",
                    Constant.DEFAULT_FIELD_DELIMITER));
        }

        // warn: fieldDelimiter could not be '' for no fieldDelimiter
        char fieldDelimiter = config.getChar(Key.FIELD_DELIMITER, Constant.DEFAULT_FIELD_DELIMITER);
        csvBuilder.setDelimiter(fieldDelimiter);

        List<String> headers = config.getList(Key.HEADER, String.class);
        if (null != headers && !headers.isEmpty()) {
//            unstructuredWriter.writeOneRecord(headers);
            csvBuilder.setHeader(headers.toArray(new String[0]));
        }

        Record record;
        CSVPrinter csvPrinter = new CSVPrinter(writer, csvBuilder.build());
        while ((record = lineReceiver.getFromReader()) != null) {
            final List<String> result = recordToList(record, nullFormat, dateParse, taskPluginCollector);
            if (result != null) {
                csvPrinter.printRecord(result);
            }
        }

        // warn:由调用方控制流的关闭
        // IOUtils.closeQuietly(unstructuredWriter);
    }

    public static List<String> recordToList(Record record, String nullFormat, DateFormat dateParse, TaskPluginCollector taskPluginCollector)
    {
        try {
            List<String> splitRows = new ArrayList<>();
            int recordLength = record.getColumnNumber();
            if (0 != recordLength) {
                Column column;
                for (int i = 0; i < recordLength; i++) {
                    column = record.getColumn(i);
                    if (null == column || null == column.getRawData() || column.asString().equals(nullFormat)) {
                        // warn: it's all ok if nullFormat is null
                        splitRows.add(nullFormat);
                    }
                    else {
                        // warn: it's all ok if nullFormat is null
                        boolean isDateColumn = column instanceof DateColumn;
                        if (!isDateColumn) {
                            splitRows.add(column.asString());
                        }
                        else {
                            if (null != dateParse) {
                                splitRows.add(dateParse.format(column.asDate()));
                            }
                            else {
                                splitRows.add(column.asString());
                            }
                        }
                    }
                }
            }
            return splitRows;
        }
        catch (Exception e) {
            // warn: dirty data
            taskPluginCollector.collectDirtyRecord(record, e);
            return null;
        }
    }
}
