package src.files;

import src.core.config.CacheConfigConstants;
import src.files.exception.FileInvalidFormatException;

import java.io.File;
import java.nio.file.Path;
import java.util.Comparator;

public class DataFilesProcessingHelper {

    private final FileProcessingHelper fileProcessingHelper = new FileProcessingHelper();
    private final String dataFilesDirectoryLocation = System.getProperty(CacheConfigConstants.DATA_DIRECTORY_LOCATION);
    private final String dataFilesExtension = System.getProperty(CacheConfigConstants.DATA_FILES_EXTENSION);

    public boolean isDataFile(File file) {
        return fileProcessingHelper.getFileExtension(file).equals(System.getProperty(CacheConfigConstants.DATA_FILES_EXTENSION));
    }

    public long getDataFileSequenceNumber(File dataFile) throws FileInvalidFormatException {
        final int start = dataFile.getName().lastIndexOf('-') + 1;
        final int end = dataFile.getName().lastIndexOf('.');
        final String fileSequenceNumber = dataFile.getName().substring(start, end);
        try {
            return Integer.parseInt(fileSequenceNumber);
        } catch (NumberFormatException e) {
            throw new FileInvalidFormatException();
        }
    }

    public Comparator<File> getDataFilesComparatorOldComesFirst() {
        return (firstFile, secondFile) -> {
            try {
                return getDataFileSequenceNumber(firstFile) > getDataFileSequenceNumber(secondFile) ? 1 : -1;
            } catch (FileInvalidFormatException e) {
                throw new RuntimeException(e);
            }
        };
    }

    public Comparator<File> getDataFilesComparatorOldComesLast() {
        return (firstFile, secondFile) -> {
            try {
                return getDataFileSequenceNumber(firstFile) > getDataFileSequenceNumber(secondFile) ? -1 : 1;
            } catch (FileInvalidFormatException e) {
                throw new RuntimeException(e);
            }
        };
    }

    public Path createAbsolutePathForFileWithNumber(long logFileSequenceNumber) {
        return Path.of(dataFilesDirectoryLocation + File.separator + CacheConfigConstants.DATA_FILE_NAME_PREFIX + "-" + logFileSequenceNumber + "." + dataFilesExtension);
    }
}
