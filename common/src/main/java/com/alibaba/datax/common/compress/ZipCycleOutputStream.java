
package com.alibaba.datax.common.compress;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipCycleOutputStream
        extends OutputStream
{

    private final ZipOutputStream zipOutputStream;

    public ZipCycleOutputStream(OutputStream out, String fileName)
            throws IOException
    {
        this.zipOutputStream = new ZipOutputStream(out);
        ZipEntry currentZipEntry = new ZipEntry(fileName);
        this.zipOutputStream.putNextEntry(currentZipEntry);
    }

    @Override
    public void write(int b)
            throws IOException
    {
        byte[] data = {(byte) b};
        this.zipOutputStream.write(data, 0, data.length);
    }

    @Override
    public void close()
            throws IOException
    {
        this.zipOutputStream.closeEntry();
        this.zipOutputStream.close();
    }
}
