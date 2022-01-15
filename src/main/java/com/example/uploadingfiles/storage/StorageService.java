package com.example.uploadingfiles.storage;

import org.springframework.core.io.Resource;

import java.io.File;
import java.nio.file.Path;
import java.util.stream.Stream;

public interface StorageService {

	void init();

	void processAndStore(File file, String originalName);

	Stream<Path> loadAll();

	Path load(String filename);

	Resource loadAsResource(String filename);

	void deleteAll();

}
