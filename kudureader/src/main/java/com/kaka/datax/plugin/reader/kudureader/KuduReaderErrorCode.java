

package com.kaka.datax.plugin.reader.kudureader;

import com.alibaba.datax.common.spi.ErrorCode;


public enum KuduReaderErrorCode implements ErrorCode {

    ILLEGAL_VALUE("Illegal parameter value","参数不合法"),
    ILLEGAL_ADDRESS("Illegal address","不合法的Kudu Master Addresses"),
    UNKNOWN_EXCEPTION("Unknown exception","未知异常");

    private final String code;

    private final String description;

    KuduReaderErrorCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDescription() {
        return description;
    }
}

