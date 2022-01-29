package com.example.uploadingfiles.storage;

import com.example.uploadingfiles.processing.barcode.creation.EAN;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFClientAnchor;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PreDestroy;
import java.io.*;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

@Component
@SessionScope
public class EANAppender {
    private final Logger logger = LogManager.getRootLogger();
    private static final short WYSOKOSC_WIERSZA = (short) 1900;
    private static final int SZEROKOSC_EAN = 16000;
    private static final double SKALA_X_OBRAZKA = 0.85;
    private static final double SKALA_Y_OBRAZKA = 0.85;
    private static final int ODEPCHNIECIE_Y_OBRAZKA = 42;
    private static final int ODEPCHNIECIE_X_OBRAZKA = 140;
    private Path result;

    @Async
    public void asyncProcess(MultipartFile multipart, File file) {

        if (file.getTotalSpace()==0) {
            throw new StorageException("Pusty plik.");
        }

        String originalFilename = multipart.getOriginalFilename();
        String filename = originalFilename.replace(".xls", "_")
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss"))+".xls";

        Path destinationFile = Paths.get(filename);

        try {
            overrideSourceXLS(destinationFile, file);
        } catch (IOException e) {
            System.err.println("Nie udało się zrobić przetwarzania tego pliku");
            e.printStackTrace();
        }

        result = destinationFile;
        logger.info("Zakończono przetwarzanie asynchroniczne pliku: " + originalFilename);
    }

    private void overrideSourceXLS(Path destinationFile, File file) throws IOException {
        InputStream inputStream = new FileInputStream(file);
        HSSFWorkbook workbook = new HSSFWorkbook(inputStream);
        Sheet sheet = workbook.getSheetAt(0);

        sheet.rowIterator().forEachRemaining(
                row -> {

                    try {
                        if (row.getCell(0) == null) {
                            return;
                        }

                        row.setHeight(WYSOKOSC_WIERSZA);
                        makeFreeColumnForEANs(row);

                        String ean = String.format("%.0f", row.getCell(0).getNumericCellValue());

                        byte[] eanImage = EAN.generate(ean, 600, 300);

                        addImage(workbook, sheet, row, eanImage);

                        logger.info("Rozpoznałem [{}]", ean);
                    } catch (IllegalArgumentException e) {
                        logger.error("Nieprawidłowy ean w wierszu [{}]", row.getRowNum());
                    } catch (IOException e) {
                        logger.error("Błąd podczas tworzenia obrazka");
                    } catch (Exception e) {
                        logger.error("Nie udało się przetworzyć wiersza: "+row.getRowNum());
                        e.printStackTrace();
                    }
                }
        );

        adjustColumnWidth(sheet);

        saveChanged(workbook, destinationFile.toFile());
    }

    private static void adjustColumnWidth(Sheet sheet) {
        for (int i = 0; i < 100; i++) {
            sheet.autoSizeColumn(i);
        }

        sheet.setColumnWidth(1, SZEROKOSC_EAN);
    }

    private static void saveChanged(Workbook workbook, File file) throws IOException {

        FileOutputStream outputStream = new FileOutputStream(file);
        workbook.write(outputStream);
        workbook.close();
        outputStream.close();
    }

    public void clearResultPath() {
        if (result != null) {
            boolean delete = result.toFile().delete();
            System.err.println("Kasuję plik tymczasowy. Udało się? : " + delete);
        }
        this.result = null;
    }

    public Resource loadAsResource(String filename) {
        try {
            return new UrlResource(result.toUri());
        }
        catch (MalformedURLException e) {
            throw new StorageFileNotFoundException("Could not read file: " + filename, e);
        }
    }

    public Stream<Path> loadAll(){
        return Stream.of(result);
    }

    public Path getResult() {
        return result;
    }

    private static void makeFreeColumnForEANs(Row row) {

        row.shiftCellsRight(1,row.getLastCellNum()-1,1);
    }

    private static void addImage(Workbook workbook, Sheet sheet, Row row, byte[] eanImage) {

        int pictureId = workbook.addPicture(eanImage, Workbook.PICTURE_TYPE_PNG);
        Drawing<?> drawing = sheet.createDrawingPatriarch();

        HSSFClientAnchor anchor = new HSSFClientAnchor();
        anchor.setCol1(1); // Sets the column (0 based) of the first cell.
        anchor.setCol2(2); // Sets the column (0 based) of the Second cell.
        anchor.setRow1(row.getRowNum()); // Sets the row (0 based) of the first cell.
        anchor.setRow2(row.getRowNum()+1); // Sets the row (0 based) of the Second cell.

        anchor.setDy1(anchor.getDy1() + ODEPCHNIECIE_Y_OBRAZKA);
        anchor.setDx1(anchor.getDx1() + ODEPCHNIECIE_X_OBRAZKA);

        Picture picture = drawing.createPicture(anchor, pictureId);
        picture.resize(SKALA_X_OBRAZKA, SKALA_Y_OBRAZKA);
    }

    @PreDestroy
    public void close() {
        clearResultPath();
    }
}
