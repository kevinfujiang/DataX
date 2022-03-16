package com.alibaba.datax.plugin.reader.hbase11xsqlreader;

import com.alibaba.datax.common.util.Configuration;
import org.junit.Test;

import java.util.List;

import static junit.framework.Assert.assertEquals;

/**
 * Created by shf on 16/7/20.
 */
public class HbaseSQLHelperTest {

    private String jsonStr = "{\n" +
            "        \"hbaseConfig\": {\n" +
            "            \"hbase.zookeeper.quorum\": \"10.24.68.187:2181/hbase;\"\n" +
            "        },\n" +
            "        \"table\": \"POC_TRADEHIS_4\",\n" +
            "        \"column\": [\"REQUESTNO\", \"SK_CONFIRMDATE\", \"CUSTOMERID\", \"TRADETYPE\", \"PRODUCTCNNAME\", \"SHARETYPE\", \"SHARES\",\"AMOUNT\",\"NETVALUE\",\"TOTALFARE\",\"AGENCYNAME\"]\n" +
            "    }";


    @Test
    public void testParseConfig() {
        Configuration config = Configuration.from(jsonStr);
        HbaseSQLReaderConfig readerConfig = HbaseSQLHelper.parseConfig(config);
        System.out.println("tablenae = " +readerConfig.getTableName() +",zk = " +readerConfig.getZkUrl());
        assertEquals("POC_TRADEHIS_4", readerConfig.getTableName());
        assertEquals("10.24.68.187:2181", readerConfig.getZkUrl());
    }

    @Test
    public void testSplit() {
        Configuration config = Configuration.from(jsonStr);
        HbaseSQLReaderConfig readerConfig = HbaseSQLHelper.parseConfig(config);
        List<Configuration> splits = HbaseSQLHelper.split(readerConfig);
        System.out.println("split size = " + splits.size());
    }
}
