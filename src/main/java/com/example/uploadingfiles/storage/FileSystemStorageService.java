package com.example.uploadingfiles.storage;

import com.example.uploadingfiles.processing.barcode.creation.EAN;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFClientAnchor;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.*;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

@Service
public class FileSystemStorageService implements StorageService {

	private final Logger logger = LogManager.getRootLogger();
	public static final short WYSOKOSC_WIERSZA = (short) 1300;
	public static final int SZEROKOSC_EAN = 7000;
	public static final double SKALA_X_OBRAZKA = 0.85;
	public static final double SKALA_Y_OBRAZKA = 0.85;
	public static final int ODEPCHNIECIE_Y_OBRAZKA = 42;
	public static final int ODEPCHNIECIE_X_OBRAZKA = 140;

	private final Path rootLocation;

	@Autowired
	public FileSystemStorageService(StorageProperties properties) {
		this.rootLocation = Paths.get(properties.getLocation());
	}

	@Override
	public void processAndStore(File file, String originalFilename) {
		try {
			if (file.getTotalSpace()==0) {
				throw new StorageException("Pusty plik.");
			}

			String filename = originalFilename.replace(".xls", "_")
					+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss"))+".xls";

			Path destinationFile = this.rootLocation.resolve(
					Paths.get(filename))
					.normalize().toAbsolutePath();
			if (!destinationFile.getParent().equals(this.rootLocation.toAbsolutePath())) {
				// This is a security check
				throw new StorageException("Błąd zapisu pliku");
			}

			overrideSourceXLS(destinationFile, file);
		}
		catch (IOException e) {
			throw new StorageException("Nie udało się zapisać pliku.", e);
		}
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

	public static void saveChanged(Workbook workbook, File file) throws IOException {

		FileOutputStream outputStream = new FileOutputStream(file);
		workbook.write(outputStream);
		workbook.close();
//		logger.error("Zapisałem poprawnie plik wynikowy");
	}

	private static void adjustColumnWidth(Sheet sheet) {
		for (int i = 0; i < 100; i++) {
			sheet.autoSizeColumn(i);
		}

		sheet.setColumnWidth(1, SZEROKOSC_EAN);
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

	private static void makeFreeColumnForEANs(Row row) {

		row.shiftCellsRight(1,row.getLastCellNum()-1,1);
	}

	@Override
	public Stream<Path> loadAll() {
		try {
			return Files.walk(this.rootLocation, 1)
				.filter(path -> !path.equals(this.rootLocation))
				.map(this.rootLocation::relativize);
		}
		catch (IOException e) {
			throw new StorageException("Failed to read stored files", e);
		}

	}

	@Override
	public Path load(String filename) {
		return rootLocation.resolve(filename);
	}

	@Override
	public Resource loadAsResource(String filename) {
		try {
			Path file = load(filename);
			Resource resource = new UrlResource(file.toUri());
			if (resource.exists() || resource.isReadable()) {
				return resource;
			}
			else {
				throw new StorageFileNotFoundException(
						"Could not read file: " + filename);

			}
		}
		catch (MalformedURLException e) {
			throw new StorageFileNotFoundException("Could not read file: " + filename, e);
		}
	}

	@Override
	public void deleteAll() {
		FileSystemUtils.deleteRecursively(rootLocation.toFile());
	}

	@Override
	public void init() {
		try {
			Files.createDirectories(rootLocation);
		}
		catch (IOException e) {
			throw new StorageException("Could not initialize storage", e);
		}
	}
}
