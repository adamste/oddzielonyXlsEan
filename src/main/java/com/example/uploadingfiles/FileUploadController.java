package com.example.uploadingfiles;

import com.example.uploadingfiles.storage.EANAppender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.view.RedirectView;

import java.io.File;
import java.nio.file.Files;
import java.util.stream.Collectors;

@Controller
public class FileUploadController {

	private final Logger logger = LogManager.getRootLogger();

	@Autowired
	private EANAppender eanAppender;

	@GetMapping("/")
	public String listUploadedFiles() {

		logger.info("Zalogowany user dostanie formularz do uploadu");

		return "uploadForm";
	}

	@GetMapping("/files/{filename:.+}")
	@ResponseBody
	public ResponseEntity<Resource> serveFile(@PathVariable String filename) {

		Resource file = eanAppender.loadAsResource(filename);
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
				"attachment; filename=\"" + filename + "\"").body(file);
	}

	@PostMapping("/")
	public RedirectView handleFileUpload(@RequestParam("file") MultipartFile file, Model model) throws Exception {

		eanAppender.clearResultPath();

		logger.info("Proces wrzucania pliku o nazwie: " + file.getOriginalFilename() + " i rozmiarze" + file.getSize());

		File temp = new File(String.valueOf(Files.createTempFile("hello", ".xls")));
		temp.deleteOnExit();
		file.transferTo(temp);

		eanAppender.asyncProcess(file, temp);

		return new RedirectView("result");
	}

	@GetMapping("/result")
	public String listUploadedFiles(Model model) {

		if (eanAppender.getResult() != null) {
			model.addAttribute("files", eanAppender.loadAll().map(
					path -> MvcUriComponentsBuilder.fromMethodName(FileUploadController.class,
							"serveFile", path.getFileName().toString()).build().toUri().toString())
					.collect(Collectors.toList()));
		}
		return "uploaded";
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<?> handleException(Exception exc) {

		logger.error(exc);

		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(exc.getMessage());
	}

}
