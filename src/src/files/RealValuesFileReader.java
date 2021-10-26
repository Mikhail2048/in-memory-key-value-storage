package src.files;

import src.core.models.DataFilePointer;
import src.core.models.Record;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RealValuesFileReader {

    private final DataFilesProcessingHelper dataFilesProcessingHelper;
    private final String EMPTY_STRING = "";

    public RealValuesFileReader() {
        this.dataFilesProcessingHelper = new DataFilesProcessingHelper();
    }

    public ConcurrentMap<String, String> readKeyValuePairs(File sourceDataFile) {
        ConcurrentMap<String, String> keyValueMap = new ConcurrentHashMap<>();
        try (final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(sourceDataFile)))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                final String[] parts = line.split(":");
                keyValueMap.put(parts[0], parts[1]);
            }
            return keyValueMap;
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public String findValueByKeyInTargetFile(String key, DataFilePointer targetDataFilePointer) {
        final Path absolutePathForTargetDataFile = dataFilesProcessingHelper.createAbsolutePathForFileWithNumber(targetDataFilePointer.getSequenceNumber());
        try (final RandomAccessFile targetRandomAccessFile = new RandomAccessFile(absolutePathForTargetDataFile.toString(), "r")) {
            targetRandomAccessFile.seek(targetDataFilePointer.getKeyValueByteOffsetOnDiskRedBlackTree().get(key));
            StringBuilder result = new StringBuilder();
            char readSymbol;
            while ((readSymbol = ((char) targetRandomAccessFile.read())) != '\n') {
                result.append(readSymbol);
            }
            return result.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String scanDataFileFromByteOffset(long dataFileSeqNumber, int from) {
        try {
            final int sizeInBytes = Math.toIntExact(Files.size(dataFilesProcessingHelper.createAbsolutePathForFileWithNumber(dataFileSeqNumber)));
            return this.scanDataFileFromToByteOffset(dataFileSeqNumber, from, sizeInBytes);
        } catch (IOException e) {
            return EMPTY_STRING;
        }
    }

    /**
     * Scans particular Data File from the particular offset to the particular offset
     * @param dataFileSeqNumber - sequence number of data file to read
     * @param from indicates the initial offset from where to read
     * @param to indicates the final offset to where to read
     * @return String, constructed from read bytes
     */
    public String scanDataFileFromToByteOffset(long dataFileSeqNumber, int from, int to) {
        validateRange(from, to);
        final Path absolutePathForFileToScan = dataFilesProcessingHelper.createAbsolutePathForFileWithNumber(dataFileSeqNumber);
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(absolutePathForFileToScan.toString(), "r")) {
            byte[] buffer = new byte[(to - from) + 1];
            randomAccessFile.read(buffer, from, (to - from) + 1);
            return new String(buffer, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return EMPTY_STRING;
        }
    }

    private void validateRange(int from, int to) {
        if (from > to) throw new IllegalArgumentException(String.format("Cannot scan file from byte with number '%s' to byte with number '%s'%n", from, to));
    }
}