package src.core.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

public class ConfigurationFileParser {

    private final String CONFIG_FILE_NAME = "aisa.properties";
    private final String CONFIGURATION_FILE_PATH = "/home/misha/designing_data_instensive_applications/3/" + CONFIG_FILE_NAME;
    private final PropertiesValidator propertiesValidator = new PropertiesValidator();

    public void parseConfiguration() throws IOException {
        try (final InputStreamReader reader = new InputStreamReader(new FileInputStream(CONFIGURATION_FILE_PATH))) {
            final Properties properties = new Properties();
            properties.load(reader);
            validateProperties(properties);
            logReadProperties(properties);
            System.setProperties(properties);
        }
    }

    private void validateProperties(Properties properties) {
        propertiesValidator.checkValidIntegerElseExit(CacheConfigConstants.MAX_DATA_FILES_AMOUNT, properties.getProperty(CacheConfigConstants.MAX_DATA_FILES_AMOUNT));
        propertiesValidator.checkValidIntegerElseExit(CacheConfigConstants.VALUE_MAX_SIZE_IN_BYTES, properties.getProperty(CacheConfigConstants.VALUE_MAX_SIZE_IN_BYTES));
        propertiesValidator.checkValidIntegerElseExit(CacheConfigConstants.DATA_FILES_MAX_SIZE_IN_KILOBYTES, properties.getProperty(CacheConfigConstants.DATA_FILES_MAX_SIZE_IN_KILOBYTES));
    }

    private void logReadProperties(Properties properties) {
        System.out.println("----------------------------------------Read Properties----------------------------------------");
        System.out.println(CacheConfigConstants.DATA_FILES_EXTENSION + " : " + properties.getProperty(CacheConfigConstants.DATA_FILES_EXTENSION));
        System.out.println(CacheConfigConstants.DATA_DIRECTORY_LOCATION + " : " + properties.getProperty(CacheConfigConstants.DATA_DIRECTORY_LOCATION));
        System.out.println(CacheConfigConstants.MAX_DATA_FILES_AMOUNT + " : " + properties.getProperty(CacheConfigConstants.MAX_DATA_FILES_AMOUNT));
        System.out.println(CacheConfigConstants.DATA_FILES_MAX_SIZE_IN_KILOBYTES + " : " + properties.getProperty(CacheConfigConstants.DATA_FILES_MAX_SIZE_IN_KILOBYTES));
        System.out.println(CacheConfigConstants.VALUE_MAX_SIZE_IN_BYTES + " : " + properties.getProperty(CacheConfigConstants.VALUE_MAX_SIZE_IN_BYTES));
        System.out.println("----------------------------------------End----------------------------------------");
    }
}