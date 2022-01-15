package com.example.uploadingfiles.excel.reading;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.IOException;
import java.io.InputStream;

public class XLSReader {

    public static Workbook getWorkbook(InputStream fileInputStream) throws IOException {

        return new HSSFWorkbook(fileInputStream);
    }
}
