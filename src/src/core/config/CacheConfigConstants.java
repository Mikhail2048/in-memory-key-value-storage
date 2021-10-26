package src.core.config;

public class CacheConfigConstants {

    public static final String DATA_FILE_NAME_PREFIX = "cache-data";

    /**
     * Location (absolute path) of the writable data ss-tables file to which next snapshot of
     * the in-memory table must be written
     */
    public static final String CURRENT_WRITABLE_SS_TABLE_FILE_LOCATION = "currentWritableSSTableFileLocation";

    /**
     * This file represents temporary log. Each time user ires PUT operation to our database,
     * the data that needs to be put will be stored initially in the in-memory data structure (red-black tree
     * to be more precise), but not on real data file. In order to not to loose this data we will also
     * store this PUT record in the temporary log file. This log file does not need to be sorted, because it serves
     * only recovery purposes. This property indicates the location of this log file
     */
    public static final String TEMPORARY_LOG_LOCATION = "currentTemporaryLogLocation";
    public static final String DATA_DIRECTORY_LOCATION = "data.directory.location";
    public static final String DATA_FILES_EXTENSION = "data.files.extension";
    public static final String MAX_DATA_FILES_AMOUNT = "data.files.max.amount";
    public static final String DATA_FILES_MAX_SIZE_IN_KILOBYTES = "data.files.max.kilobytes.size";

    /**
     * Maximum size of the value (in bytes), that can be provided to AISA during {@link src.core.models.CommandType#PUT}
     */
    public static final String VALUE_MAX_SIZE_IN_BYTES = "value.max.byte.size";
}