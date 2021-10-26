package src.files;

import src.core.InMemoryRedBlackTreesConstructor;
import src.core.config.CacheConfigConstants;
import src.core.models.DataFilePointer;
import src.core.models.KeyFileEntry;
import src.core.models.Record;
import src.files.exception.FileInvalidFormatException;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * This class is responsible to manage local in-memory hash tables
 */
public class LocalFilePointersManager {

    /**
     * Firstly comes more fresh data files, and lastly comes more old data files
     * So, therefore, the current writeable file represented here as the first
     * element in the LinkedList
     */
    private final LinkedList<DataFilePointer> inMemoryMaps;
    private final DataFilesProcessingHelper dataFilesProcessingHelper;
    private final InMemoryRedBlackTreesConstructor inMemoryRedBlackTreesConstructor;
    private final NavigableMap<String, String> memoryRedBlackTree;
    private final RealValuesFileReader realValuesFileReader;

    private final Object writeLock = new Object();
    private static LocalFilePointersManager instance;

    /**
     * TODO: Make thread safe.
     */
    public static LocalFilePointersManager getInstance() {
        if (instance == null) {
            return (instance = new LocalFilePointersManager());
        }
        return instance;
    }

    private LocalFilePointersManager() {
        this.dataFilesProcessingHelper = new DataFilesProcessingHelper();
        this.inMemoryRedBlackTreesConstructor = new InMemoryRedBlackTreesConstructor();
        this.inMemoryMaps = inMemoryRedBlackTreesConstructor.constructDataFilesPointers();
        this.memoryRedBlackTree = Collections.synchronizedNavigableMap(new TreeMap<>()); //TODO: find out a better approach then synchronization
        this.realValuesFileReader = new RealValuesFileReader();
    }

    /**
     * Replaces last amount {@param amountOfSquashedMaps} of an in memory maps with the new one.
     * This method will provide an API for {@link src.files.FileSegmentsManager}, where the latter
     * will squash the maps and force {@link LocalFilePointersManager} to update its in-memory state
     *
     * @param amountOfSquashedMaps - amount of in-memory maps at the end of the list (hence, the most stale maps)
     * @param newlyConstructedRedBlackTree - new Map that have been created by squashing the maps at the end
     */
    public void replaceMapsSynchronized(int amountOfSquashedMaps, NavigableMap<String, Long> newlyConstructedRedBlackTree, long newDataFileSequenceNumber) {
        synchronized (inMemoryMaps) {
            for (int i = 0; i < amountOfSquashedMaps; i++) {
                inMemoryMaps.removeLast();
            }
            inMemoryMaps.addLast(new DataFilePointer(newDataFileSequenceNumber, newlyConstructedRedBlackTree));
        }
    }

    public void putAllIntoInMemoryTree(Collection<Record> records) {
        records.forEach(record -> memoryRedBlackTree.put(record.getKey(), record.getValue()));
    }

    public void putKeyValuePairToLocalTree(String key, String value) throws IOException {
        doubleCheckLockingOnInMemoryRBTSize();
        final Path temporaryLogFileLocation = Paths.get(System.getProperty(CacheConfigConstants.TEMPORARY_LOG_LOCATION));
        Files.write(
                temporaryLogFileLocation,
                (key + ":" + value + "\n").getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.APPEND
        );
        this.memoryRedBlackTree.put(key, value);
    }

    private void doubleCheckLockingOnInMemoryRBTSize() {
        if (memoryRedBlackTree.size() >= 5000) {
            synchronized (writeLock) {
                if (memoryRedBlackTree.size() >= 5000) {
                    flushInMemoryRedBlackTreeToDisk();
                }
            }
        }
    }

    private void flushInMemoryRedBlackTreeToDisk() {
        try (final FileChannel temporaryLogFileChannel = FileChannel.open(Path.of(System.getProperty(CacheConfigConstants.TEMPORARY_LOG_LOCATION)), StandardOpenOption.WRITE);
             final FileLock logFileLock = temporaryLogFileChannel.lock()) {
            persistCurrentInMemoryRBTToSSTableOnDisk();
        } catch (FileInvalidFormatException | IOException e) {
            e.printStackTrace();
        }
    }

    private void persistCurrentInMemoryRBTToSSTableOnDisk() throws FileInvalidFormatException, IOException {
        final DataFilePointer currentWritableSSTableFilePointer = new DataFilePointer(
                dataFilesProcessingHelper.getDataFileSequenceNumber(new File(System.getProperty(CacheConfigConstants.CURRENT_WRITABLE_SS_TABLE_FILE_LOCATION)))
        );
        final NavigableMap<String, Long> archivedTreeMap = new TreeMap<>();
        StringBuilder dataToBeFlushedToDisk = new StringBuilder();
        AtomicLong valueByteOffset = new AtomicLong();
        memoryRedBlackTree.entrySet()// <-- here we traverse the RBT, and the order is ascending
                .stream()
                .peek(keyValueEntry -> dataToBeFlushedToDisk.append(keyValueEntry.getKey()).append(":").append(keyValueEntry.getValue()).append("\n"))
                .forEach(keyValueEntry -> {
                    final long currentValueByteOffset = valueByteOffset.addAndGet((keyValueEntry.getKey() + ":").getBytes(StandardCharsets.UTF_8).length + 1);
                    archivedTreeMap.put(keyValueEntry.getKey(), currentValueByteOffset);
                    valueByteOffset.addAndGet((keyValueEntry.getValue() + "\n").getBytes(StandardCharsets.UTF_8).length);
                });
        writePassedDataToCurrentSSTable(dataToBeFlushedToDisk);
        currentWritableSSTableFilePointer.setKeyValueByteOffsetOnDiskRedBlackTree(archivedTreeMap);
        inMemoryMaps.addFirst(currentWritableSSTableFilePointer);
        memoryRedBlackTree.clear();
        FileSegmentsManager.getInstance().truncateLogFile();
    }

    private void writePassedDataToCurrentSSTable(StringBuilder dataToBeFlushedToDisk) throws IOException {
        Files.write(
                Path.of(System.getProperty(CacheConfigConstants.CURRENT_WRITABLE_SS_TABLE_FILE_LOCATION)),
                dataToBeFlushedToDisk.toString().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE
        );
    }

    public String getValueForKey(String key) {
        String value;
        if ((value = memoryRedBlackTree.get(key)) == null) {
            value = findValueInArchivedSegments(key);
        }
        return value;
    }

    public List<String> getValuesInRange(String from, String to) {
        final SortedMap<String, String> recordsFoundInRBT = memoryRedBlackTree.subMap(from, to);
        List<String> resultValues = new ArrayList<>(recordsFoundInRBT.values());
        Set<KeyFileEntry> alreadyFoundKey = recordsFoundInRBT.keySet().stream().map(KeyFileEntry::new).collect(Collectors.toSet());
        traverseDataFilePointersAndFillResultValues(resultValues, alreadyFoundKey, from, to);
        return resultValues;
    }

    private void traverseDataFilePointersAndFillResultValues(List<String> resultValues, Set<KeyFileEntry> alreadyFoundKeys, String from, String to) {
        traverseInMemoryArchivedRBTAndFillSetOfFoundKeys(alreadyFoundKeys, from, to);
        final Map<Long, List<Long>> dataFileSeqNumberScanIntervalMap = createDataFileIntervalScanMapFromFoundKeys(alreadyFoundKeys);
        dataFileSeqNumberScanIntervalMap.entrySet().stream()
                .map(this::readDataFileOffset)
                .filter(Objects::nonNull)
                .map(this::removeTailIfRequired)
                .forEach(tailedDataFileRawContent -> {
                    final byte[] readBytes = tailedDataFileRawContent.getRawData().getBytes(StandardCharsets.UTF_8);
                    alreadyFoundKeys.stream()
                            .filter(keyFileEntry -> keyFileEntry.getDataFileSequenceNumber() == tailedDataFileRawContent.getDataFileSequenceNumber())
                            .map(KeyFileEntry::getByteOffsetOfTargetValue)
                            .map(byteOffsetOfTheValueWeAreInterestedIn -> parseValue(readBytes, Math.toIntExact(byteOffsetOfTheValueWeAreInterestedIn)))
                            .forEach(resultValues::add);
                });
    }

    private String parseValue(byte[] readBytes, int byteOffsetOfTheValueWeAreInterestedIn) {
        int currentByteIndex = byteOffsetOfTheValueWeAreInterestedIn;
        StringBuilder value = new StringBuilder();
        while (readBytes[currentByteIndex] != '\n') {
            value.append(readBytes[currentByteIndex]);
            currentByteIndex++;
        }
        return value.toString();
    }

    private DataFileRawContent removeTailIfRequired(DataFileRawContent dataFileRawContent) {
        if (dataFileRawContent.getRawData().charAt(dataFileRawContent.getRawData().length() - 1) != '\n') {
            dataFileRawContent.setRawData(dataFileRawContent.getRawData().substring(0, dataFileRawContent.getRawData().lastIndexOf("\n") + 1)); //Excluding the last element - since it does not belongs to our
        }
        return dataFileRawContent;
    }

    /**
     * @param dataFileSequenceNumberBordersReadEntry represents {@link Map.Entry}, where key is data file sequence number,
     *                                               and value is list, that contains borders in which we should read data file
     * @return read raw data from passed data file sequence number (derived from entry) as string, or null, if we do not need to
     *                                              read anything from this file.
     *                                              NOTE: the returned string may contain extra read bytes, that belongs
     *                                              to another key/value pair. The method, that invoked {@link #readDataFileOffset(Map.Entry)}
     *                                              needs to filter it <b>himself</b>
     */
    private DataFileRawContent readDataFileOffset(Map.Entry<Long, List<Long>> dataFileSequenceNumberBordersReadEntry) {
        if (dataFileSequenceNumberBordersReadEntry.getValue() != null) {
            final int numberOfLastByteToRead = determineLastByteToRead(dataFileSequenceNumberBordersReadEntry);
            return new DataFileRawContent(
                    dataFileSequenceNumberBordersReadEntry.getKey(),
                    realValuesFileReader.scanDataFileFromToByteOffset(
                            dataFileSequenceNumberBordersReadEntry.getKey(),
                            Math.toIntExact(dataFileSequenceNumberBordersReadEntry.getValue().get(0)),
                            numberOfLastByteToRead
                    )
            );
        }
        return null;
    }

    private int determineLastByteToRead(Map.Entry<Long, List<Long>> dataFileSequenceNumberBordersReadEntry) {
        final int numberOfLastByteToRead;
        if (dataFileSequenceNumberBordersReadEntry.getValue().size() == 1) {
            numberOfLastByteToRead = determineWhereToStopReading(Math.toIntExact(dataFileSequenceNumberBordersReadEntry.getValue().get(0)), dataFileSequenceNumberBordersReadEntry.getKey());
        } else {
            numberOfLastByteToRead = determineWhereToStopReading(Math.toIntExact(dataFileSequenceNumberBordersReadEntry.getValue().get(1)), dataFileSequenceNumberBordersReadEntry.getKey());
        }
        return numberOfLastByteToRead;
    }

    private int determineWhereToStopReading(int numberOfByteToReadFrom, Long dataFileSequenceNumber) {
        try {
            final int dataFileSizeInBytes = Math.toIntExact(Files.size(dataFilesProcessingHelper.createAbsolutePathForFileWithNumber(dataFileSequenceNumber)));
            final int maxValueSizeInBytes = Integer.parseInt(System.getProperty(CacheConfigConstants.VALUE_MAX_SIZE_IN_BYTES));
            return Math.min(numberOfByteToReadFrom + maxValueSizeInBytes, dataFileSizeInBytes);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.printf("Error while reading data file. Cause : %s, Message: %s%n", e.getCause(), e.getMessage());
            return numberOfByteToReadFrom;
        }
    }

    /**
     * @param alreadyFoundKeys represents the Set of {@link KeyFileEntry's}, that needs to be read in general. In this
     *                         may be, for example, 3 {@link KeyFileEntry} instances, that indicates, that data file with
     *                         sequence number as 10 contains 3 key/value pairs that we are interested in, the key cab be derived
     *                         as {@link KeyFileEntry#getKey()}, and the value can be read only from disk, but we now the data file
     *                         sequence number {@link KeyFileEntry#getDataFileSequenceNumber()} and value byte offset {@link KeyFileEntry#getByteOffsetOfTargetValue()}
     * @return map, where the key is the sequence number of the data file, and values are the two integers (or may be one)
     *              that indicate
     *              1) the start offset, from where to read the data file. The very first value that needs to be read resides here
     *              and
     *              2) the offset of the last value to be read in this data file
     *              In case if there is only one integer in the list, that means that we just need to read from this particular
     *              offset the value from disk and that just it. In other words, if in the list is only one integer, it will indicate
     *              the same as '1' value above.
     */
    private Map<Long, List<Long>> createDataFileIntervalScanMapFromFoundKeys(Set<KeyFileEntry> alreadyFoundKeys) {
        Map<Long, List<Long>> dataFileSeqNumberByteOffsets = new HashMap<>();
        alreadyFoundKeys.forEach(keyFileEntry -> dataFileSeqNumberByteOffsets.compute(
                keyFileEntry.getDataFileSequenceNumber(),
                (dataFileSeqNumber, byteOffsetsList) -> mergeCurrentKeyFileEntryWithAlreadyExistedByteOffsets(keyFileEntry, byteOffsetsList)));
        return dataFileSeqNumberByteOffsets;
    }

    private List<Long> mergeCurrentKeyFileEntryWithAlreadyExistedByteOffsets(KeyFileEntry keyFileEntry, List<Long> byteOffsetsList) {
        if (byteOffsetsList == null || byteOffsetsList.isEmpty()) {
            if (keyFileEntry.getByteOffsetOfTargetValue() != null) {
                byteOffsetsList = new ArrayList<>();
                byteOffsetsList.add(keyFileEntry.getByteOffsetOfTargetValue());
            }
        } else {
            mergeNonEmptyByteOffsetListWithCurrentKeyFileEntry(keyFileEntry, byteOffsetsList);
        }
        return byteOffsetsList;
    }

    private void mergeNonEmptyByteOffsetListWithCurrentKeyFileEntry(KeyFileEntry keyFileEntry, List<Long> byteOffsetsList) {
        if (byteOffsetsList.size() == 1) {
            addKeyFileEntryValueByteOffsetToListWithSingleElement(keyFileEntry, byteOffsetsList);
        } else {
            mergeKeyFileEntryByteOffsetWithListBothBorders(keyFileEntry, byteOffsetsList);
        }
    }

    private void mergeKeyFileEntryByteOffsetWithListBothBorders(KeyFileEntry keyFileEntry, List<Long> byteOffsetsList) {
        if (keyFileEntry.getByteOffsetOfTargetValue() > byteOffsetsList.get(1)) {
            byteOffsetsList.set(1, keyFileEntry.getByteOffsetOfTargetValue());
        } else if (keyFileEntry.getByteOffsetOfTargetValue() < byteOffsetsList.get(0)) {
            byteOffsetsList.set(0, keyFileEntry.getByteOffsetOfTargetValue());
        }
    }

    private void addKeyFileEntryValueByteOffsetToListWithSingleElement(KeyFileEntry keyFileEntry, List<Long> byteOffsetsList) {
        if (keyFileEntry.getByteOffsetOfTargetValue() < byteOffsetsList.get(0)) {
            byteOffsetsList.set(1, byteOffsetsList.get(0));
            byteOffsetsList.set(0, keyFileEntry.getByteOffsetOfTargetValue());
        } else {
            byteOffsetsList.add(keyFileEntry.getByteOffsetOfTargetValue());
        }
    }

    /**
     * The responsibility of this method is to traverse the archived Red Black Trees (or, in other ords,
     * Red Black Trees that have been flushed to the data files, and, hence, now are represented as {@link DataFilePointer}
     * objects) and fill up the passed {@code alreadyFoundKey} set. This set actually contains an information about
     * for what key, what data file needs to be read, and where exactly we must read this data file in order to find
     * the values that corresponds to the passed key
     */
    private void traverseInMemoryArchivedRBTAndFillSetOfFoundKeys(Set<KeyFileEntry> alreadyFoundKey, String fromKey, String toKey) {
        for (DataFilePointer inMemoryMap : inMemoryMaps) {
            final SortedMap<String, Long> keysFoundInCurrentArchivedRBT = inMemoryMap.getKeyValueByteOffsetOnDiskRedBlackTree().subMap(fromKey, toKey);
            keysFoundInCurrentArchivedRBT.entrySet()
                    .stream()
                    .filter(keyValueByteOffset -> !alreadyFoundKey.contains(new KeyFileEntry(keyValueByteOffset.getKey())))
                    .forEach(keyValueByteOffset -> alreadyFoundKey.add(
                            new KeyFileEntry(keyValueByteOffset.getKey(), inMemoryMap.getSequenceNumber(), keyValueByteOffset.getValue()))
                    );
        }
    }

    private String findValueInArchivedSegments(String key) {
        acquireReadLockWithExpectedValue(0);
        final String value = inMemoryMaps.stream()
                .filter(dataFilePointer -> dataFilePointer.getKeyValueByteOffsetOnDiskRedBlackTree().containsKey(key))
                .findFirst()
                .map(targetDataFilePointer -> realValuesFileReader.findValueByKeyInTargetFile(key, targetDataFilePointer))
                .orElse(null);
        decrementAmountOfReadRequests();
        return value;
    }

    private void decrementAmountOfReadRequests() {
        System.out.println("Decremented read lock to : " + FileSegmentsManager.amountOfCurrentReadRequests.decrementAndGet());
    }

    private void acquireReadLockWithExpectedValue(int expectedValueOfReads) {
        final int actualValue = FileSegmentsManager.amountOfCurrentReadRequests.compareAndExchange(expectedValueOfReads, expectedValueOfReads + 1);
        if (actualValue == expectedValueOfReads) {
            System.out.println("Read lock acquired, current lock value : " + (expectedValueOfReads + 1));
            return;
        }
        if (actualValue == -1) {
            acquireReadLockWithExpectedValue(expectedValueOfReads);
            System.out.println("Unable to acquire read lock, actual value : -1");
        } else {
            System.out.println("Unable to acquire read lock, actual value : " + actualValue);
            acquireReadLockWithExpectedValue(actualValue);
        }
    }

    private class DataFileRawContent {

        private final long dataFileSequenceNumber;

        public void setRawData(String rawData) {
            this.rawData = rawData;
        }

        private String rawData;

        public DataFileRawContent(long dataFileSequenceNumber, String rawData) {
            this.dataFileSequenceNumber = dataFileSequenceNumber;
            this.rawData = rawData;
        }

        public long getDataFileSequenceNumber() {
            return dataFileSequenceNumber;
        }

        public String getRawData() {
            return rawData;
        }
    }

    public void test() {
        final List<String> valuesInRange = getValuesInRange("28819230", "41230131");
        System.out.println(valuesInRange);
    }
}