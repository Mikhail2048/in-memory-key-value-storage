package src.core.models;

import java.util.Objects;

/**
 * This class is used during the process of the {@link CommandType#GET_RANGE}
 * It represents what keys we have already found in most recent segment data files,
 * what are the data file sequence number of the files, where this key was found, and
 * what is the byte offset of the value that is represented by this key on this file
 *
 * @see src.core.processing.GetRangeCommandProcessor
 */
public class KeyFileEntry {

    private final String key;
    private Long dataFileSequenceNumber;
    private Long byteOffsetOfTargetValue;

    public KeyFileEntry(String key, Long dataFileSequenceNumber, Long byteOffsetOfTargetValue) {
        this.key = key;
        this.dataFileSequenceNumber = dataFileSequenceNumber;
        this.byteOffsetOfTargetValue = byteOffsetOfTargetValue;
    }

    public KeyFileEntry(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public long getDataFileSequenceNumber() {
        return dataFileSequenceNumber;
    }

    public Long getByteOffsetOfTargetValue() {
        return byteOffsetOfTargetValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KeyFileEntry that = (KeyFileEntry) o;
        return key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }
}
