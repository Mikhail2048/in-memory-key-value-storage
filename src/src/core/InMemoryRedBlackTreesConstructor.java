package src.core;

import src.core.config.CacheConfigConstants;
import src.core.models.DataFilePointer;
import src.files.DataFilesProcessingHelper;
import src.files.FileProcessingHelper;
import src.files.exception.FileInvalidFormatException;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryRedBlackTreesConstructor {

    private final FileProcessingHelper fileProcessingHelper;
    private final DataFilesProcessingHelper dataFilesProcessingHelper;

    public InMemoryRedBlackTreesConstructor() {
        this.fileProcessingHelper = new FileProcessingHelper();
        this.dataFilesProcessingHelper = new DataFilesProcessingHelper();
    }

    public LinkedList<DataFilePointer> constructDataFilesPointers() {
        final String dataDirectoryAbsolutePath = System.getProperty(CacheConfigConstants.DATA_DIRECTORY_LOCATION);
        final File dataDirectory = new File(dataDirectoryAbsolutePath);
        if (dataDirectory.isDirectory()) {
            if (dataDirectory.listFiles() == null || dataDirectory.listFiles().length == 0) {
                System.out.printf("Found 0 data files in directory %s\n", dataDirectoryAbsolutePath);
                return new LinkedList<>();
            } else {
                return createInMemoryMapFromFilesInDirectory(dataDirectory);
            }
        } else {
            throw new RuntimeException(dataDirectoryAbsolutePath + " must point to a directory");
        }
    }

    private LinkedList<DataFilePointer> createInMemoryMapFromFilesInDirectory(File dataDirectory) {
        return Arrays.stream(dataDirectory.listFiles())
                .filter(file -> fileProcessingHelper.getFileExtension(file).equals(System.getProperty(CacheConfigConstants.DATA_FILES_EXTENSION)))
                .sorted(dataFilesProcessingHelper.getDataFilesComparatorOldComesLast())
                .map(this::readValuesMapFromFile)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedList::new));
    }

    public DataFilePointer readValuesMapFromFile(File dataFile) {
        NavigableMap<String, Long> keyBytesOffsetPointersMap = new TreeMap<>();
        try {
            final FileInputStream fileInputStream = new FileInputStream(dataFile);
            StringBuilder key = new StringBuilder();
            final byte[] fileContentByteArray = fileInputStream.readAllBytes(); // JVM is capable of handing file size in memory, file size no more then 100KB
            boolean isKeyRelatedByte = true;
            int amountOfKeyValuePairsRead = 0;
            for (int i = 0; i < fileContentByteArray.length; i++) {
                if (fileContentByteArray[i] == '\n') {
                    key.setLength(0);
                    isKeyRelatedByte = true;
                    continue;
                }
                if (fileContentByteArray[i] == ':') {
                    keyBytesOffsetPointersMap.put(key.toString(), (long) (i + 1));
                    isKeyRelatedByte = false;
                    amountOfKeyValuePairsRead++;
                } else {
                    if (isKeyRelatedByte) key.append((char) fileContentByteArray[i]);
                }
            }
            return createDataFilePointerOrNullIfNoRecordsRead(dataFile, keyBytesOffsetPointersMap, amountOfKeyValuePairsRead);
        } catch (IOException | FileInvalidFormatException e) {
            e.printStackTrace();
            throw new InternalError();
        }
    }

    private DataFilePointer createDataFilePointerOrNullIfNoRecordsRead(File dataFile, NavigableMap<String, Long> keyBytesOffsetPointersMap, int amountOfKeyValuePairsRead) throws FileInvalidFormatException {
        if (amountOfKeyValuePairsRead != 0) {
            System.out.printf("Loaded archived in-memory RBT from ADF: '%s'. Loaded '%s' key/value pairs into memory%n", dataFile.getName(), amountOfKeyValuePairsRead);
            return new DataFilePointer(dataFilesProcessingHelper.getDataFileSequenceNumber(dataFile), keyBytesOffsetPointersMap);
        } else {
            return null;
        }
    }
}