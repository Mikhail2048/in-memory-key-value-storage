package src.files;

import src.core.config.CacheConfigConstants;
import src.core.InMemoryRedBlackTreesConstructor;
import src.files.exception.FileInvalidFormatException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class FileSegmentsManager {

    /**
     * if value:
     * > 0 - this means that some threads are still reading data
     * = 0  this means, that there is no threads reading, AND FileSegmentsManager is NOT yet acquired decremented the value
     * < 0 - this means, that no threads are reading, and moreover no threads are capable of reading
     */
    public static volatile AtomicInteger amountOfCurrentReadRequests = new AtomicInteger(0);
    private final DataFilesProcessingHelper dataFilesProcessingHelper;
    private final InMemoryRedBlackTreesConstructor inMemoryRedBlackTreesConstructor;
    private final LocalFilePointersManager localFilePointersManager;
    private final RealValuesFileReader realValuesFileReader;

    private static FileSegmentsManager instance;

    private final String temporaryLogFileName = "tmp_log_file.log";

    private final String dataDirectoryLocation = System.getProperty(CacheConfigConstants.DATA_DIRECTORY_LOCATION);

    private FileSegmentsManager() {
        this.inMemoryRedBlackTreesConstructor = new InMemoryRedBlackTreesConstructor();
        this.dataFilesProcessingHelper = new DataFilesProcessingHelper();
        this.localFilePointersManager = LocalFilePointersManager.getInstance();
        this.realValuesFileReader = new RealValuesFileReader();
        prepareWritableSSTableDataFile();
        prepareTemporaryLogFile();
    }

    /*
     TODO: get rid of synchronized on this method
     */
    public synchronized static FileSegmentsManager getInstance() {
        if (instance == null) {
            instance = new FileSegmentsManager();
        }
        return instance;
    }

    private void prepareTemporaryLogFile() {
        try {
            if (Files.exists(Path.of(getTemporaryLogAbsolutePath()))) {
                System.out.printf("Log file already exist. Location: '%s'\n", getTemporaryLogAbsolutePath());
            } else {
                Files.createFile(Path.of(getTemporaryLogAbsolutePath()));
                System.out.printf("Creating temporary log file for AISA in-memory records recovery. Location: %s\n", getTemporaryLogAbsolutePath());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.setProperty(CacheConfigConstants.TEMPORARY_LOG_LOCATION, getTemporaryLogAbsolutePath());
    }

    public void truncateLogFile() throws IOException {
        new FileOutputStream(getTemporaryLogAbsolutePath()).getChannel().truncate(0).close();
    }

    private String getTemporaryLogAbsolutePath() {
        return dataDirectoryLocation + File.separator + temporaryLogFileName;
    }

    /**
     * Create new writable segment file for {@link LocalFilePointersManager} and set {@link CacheConfigConstants#CURRENT_WRITABLE_SS_TABLE_FILE_LOCATION}
     * appropriately
     */
    private void prepareWritableSSTableDataFile() {
        try {
            final Long theMostRecentDataFileSegmentNumber = findTheMostRecentDataFileSegmentNumber();
            final Path writableSegmentFile;
            if (theMostRecentDataFileSegmentNumber == null) {
                writableSegmentFile = createNewSSTableFileWithSeqNumber(1);
            } else {
                writableSegmentFile = createNewSSTableFileFileIfNecessary(theMostRecentDataFileSegmentNumber);
            }
            System.setProperty(CacheConfigConstants.CURRENT_WRITABLE_SS_TABLE_FILE_LOCATION, writableSegmentFile.toAbsolutePath().toString());
        } catch (FileInvalidFormatException | IOException e) {
            e.printStackTrace();
        }
    }

    private Path createNewSSTableFileFileIfNecessary(Long theMostRecentDataFileSegmentNumber) throws IOException {
        final Path absolutePathForWritableSSTableDataFile;
        if (!isDataFileWithPassedSegmentNumberEmpty(theMostRecentDataFileSegmentNumber)) {
            createNewSSTableFileWithSeqNumber(theMostRecentDataFileSegmentNumber + 1);
            absolutePathForWritableSSTableDataFile = dataFilesProcessingHelper.createAbsolutePathForFileWithNumber(theMostRecentDataFileSegmentNumber + 1);
            System.out.printf(
                    "Created new Data file : '%s'. This data file wil be utilized as writable for SSTables\n",
                    absolutePathForWritableSSTableDataFile);
        } else {
            absolutePathForWritableSSTableDataFile = dataFilesProcessingHelper.createAbsolutePathForFileWithNumber(theMostRecentDataFileSegmentNumber);
            System.out.printf(
                    "Detected empty data file : '%s'. This data file wil be utilized as writable for SSTables\n",
                    absolutePathForWritableSSTableDataFile
            );
        }
        return absolutePathForWritableSSTableDataFile;
    }

    private boolean isDataFileWithPassedSegmentNumberEmpty(Long theMostRecentDataFileSegmentNumber) throws IOException {
        return Files.size(dataFilesProcessingHelper.createAbsolutePathForFileWithNumber(theMostRecentDataFileSegmentNumber)) == 0;
    }

    public void prepareWorkspaceFilesAndInitiateMergerThread() {
        final Thread elderSegmentsSquasherThread = new Thread(this::triggerSquashElderSegmentsProcess);
        elderSegmentsSquasherThread.setDaemon(true);
        elderSegmentsSquasherThread.start();
    }

    private void triggerSquashElderSegmentsProcess() {
        while (true) {
            final File dataDirectory = new File(dataDirectoryLocation);
            final File[] dataFiles = dataDirectory.listFiles(getDataFilesFilter());
            if (dataFiles != null) {
                squashDataFilesIfNecessary(dataFiles);
            }
        }
    }

    private FilenameFilter getDataFilesFilter() {
        return (dir, name) -> dataFilesProcessingHelper.isDataFile(new File(dir.getAbsoluteFile() + File.separator + name));
    }

    private void squashDataFilesIfNecessary(File[] dataFiles) {
        if (isAmountOfDataFilesExceededTopThreshold(dataFiles)) {
            squashDataFilesTail(dataFiles);
        }
    }

    private boolean isAmountOfDataFilesExceededTopThreshold(File[] dataFiles) {
        return dataFiles.length > Integer.parseInt(System.getProperty(CacheConfigConstants.MAX_DATA_FILES_AMOUNT));
    }

    private void squashDataFilesTail(File[] dataFiles) {
        final List<File> tailDataFiles = getTailDataFiles(dataFiles);
        ConcurrentMap<String, String> squashedFileContentMap = new ConcurrentHashMap<>();
        tailDataFiles.stream().map(realValuesFileReader::readKeyValuePairs).forEach(squashedFileContentMap::putAll);
        try {
            final Path newSquashedFile = createNewSSTableFileWithSeqNumber(0);
            populateNewSquashedFileWithContentFromMap(squashedFileContentMap, newSquashedFile);
            cleanUp(inMemoryRedBlackTreesConstructor.readValuesMapFromFile(newSquashedFile.toFile()).getKeyValueByteOffsetOnDiskRedBlackTree(), tailDataFiles, newSquashedFile);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (FileInvalidFormatException e) {
            System.out.printf("%s : Invalid file format! Exception message : %s", e.getCause(), e.getMessage());
        }
    }

    private void cleanUp(NavigableMap<String, Long> squashedFileContentMap, List<File> tailDataFiles, Path squashedTemporaryFile) throws FileInvalidFormatException {
        deleteTailFiles(tailDataFiles);
        final File newDataFile = new File(tailDataFiles.get(0).getAbsolutePath());
        squashedTemporaryFile.toFile().renameTo(newDataFile);
        localFilePointersManager.replaceMapsSynchronized(
                tailDataFiles.size(),
                squashedFileContentMap,
                dataFilesProcessingHelper.getDataFileSequenceNumber(newDataFile)
        );
    }

    private void deleteTailFiles(List<File> tailDataFiles) {
        acquireLockForDeletion();
        tailDataFiles.forEach(file -> {
            try {
                Files.deleteIfExists(file.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        releaseLock();
    }

    private void releaseLock() {
        amountOfCurrentReadRequests.compareAndSet(-1, 0);
    }

    private void acquireLockForDeletion() {
        boolean lockAcquiredSuccessfully;
        do {
            lockAcquiredSuccessfully = amountOfCurrentReadRequests.compareAndSet(0, -1);
        } while (!lockAcquiredSuccessfully);
    }

    private void populateNewSquashedFileWithContentFromMap(ConcurrentMap<String, String> squashedFileContentMap, Path squashedFile) {
        squashedFileContentMap.forEach((key, value) -> {
            try {
                Files.write(
                        squashedFile,
                        (key + ":" + value + "\n").getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.APPEND
                );
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private List<File> getTailDataFiles(File[] dataFiles) {
        return Arrays.stream(dataFiles)
                .sorted(dataFilesProcessingHelper.getDataFilesComparatorOldComesFirst())
                .limit(5)
                .collect(Collectors.toList());
    }

    /**
     * @return the most recent data file segment number found in {@link #dataDirectoryLocation},
     * or {@literal null} in case that there is no such file
     */
    private Long findTheMostRecentDataFileSegmentNumber() throws FileInvalidFormatException {
        final File[] files = new File(dataDirectoryLocation).listFiles(getDataFilesFilter());
        if (files != null && files.length != 0) {
            final List<File> sortedLogFiles = Arrays.stream(files).sorted(dataFilesProcessingHelper.getDataFilesComparatorOldComesFirst()).collect(Collectors.toList());
            return dataFilesProcessingHelper.getDataFileSequenceNumber(new File(dataDirectoryLocation + File.separator + sortedLogFiles.get(sortedLogFiles.size() - 1).getName()));
        } else {
            return null;
        }
    }

    private Path createNewSSTableFileWithSeqNumber(long currentLogFileSeqNumber) throws IOException {
        return Files.createFile(dataFilesProcessingHelper.createAbsolutePathForFileWithNumber(currentLogFileSeqNumber));
    }
}
