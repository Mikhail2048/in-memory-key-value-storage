package src.core.models;

import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

public class DataFilePointer {

    /**
     * Sequential Number of the Data File. This number is primarily assigned
     * by {@link src.files.FileSegmentsManager}
     */
    private final long sequenceNumber;

    /**
     * This map contains key, as a String key, provided by User to us,
     * and the value is the the byte offset in the file, with sequence number
     * as {@link #sequenceNumber}, where the actual value resides on disk. This
     * Long represents byte offset with the exactly same place, where the client
     * MUST begin reading in order to retrieve the value. In other words, if
     * some hypothetical value resides on disk in some file, beginning at the byte offset
     * 170, then this long (value in the ConcurrentHashMap) will be exactly 170, not 169,
     * not or 171.
     */
    private NavigableMap<String, Long> keyValueByteOffsetOnDiskRedBlackTree;

    public DataFilePointer(long sequenceNumber) {
        this(sequenceNumber, new TreeMap<>());
    }

    public DataFilePointer(long sequenceNumber, NavigableMap<String, Long> keyValueByteOffsetOnDiskRedBlackTree) {
        this.sequenceNumber = sequenceNumber;
        this.keyValueByteOffsetOnDiskRedBlackTree = keyValueByteOffsetOnDiskRedBlackTree;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataFilePointer that = (DataFilePointer) o;
        return sequenceNumber == that.sequenceNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequenceNumber);
    }

    public void setKeyValueByteOffsetOnDiskRedBlackTree(NavigableMap<String, Long> keyValueByteOffsetOnDiskRedBlackTree) {
        this.keyValueByteOffsetOnDiskRedBlackTree = keyValueByteOffsetOnDiskRedBlackTree;
    }

    public NavigableMap<String, Long> getKeyValueByteOffsetOnDiskRedBlackTree() {
        return keyValueByteOffsetOnDiskRedBlackTree;
    }
}