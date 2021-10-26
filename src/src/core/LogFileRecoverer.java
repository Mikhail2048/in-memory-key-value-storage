package src.core;

import src.core.config.CacheConfigConstants;
import src.core.models.Record;
import src.files.LocalFilePointersManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;

/**
 * This class is responsible to recover lost in-memory data in case of a database failure.
 */
public class LogFileRecoverer {

    public void startRecoveryProcess() throws IOException {
        if (Files.exists(getPathToTemporaryLogFile())) {
            initiateRecovery();
        }
    }

    private void initiateRecovery() throws IOException {
        Collection<Record> recoveredRecords = new ArrayList<>();
        try (InputStream inputStream = Files.newInputStream(getPathToTemporaryLogFile(), StandardOpenOption.READ)) {
            final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String recordAsString;
            while ((recordAsString = bufferedReader.readLine()) != null) {
                String[] keyValueArray = recordAsString.split(":");
                recoveredRecords.add(new Record(keyValueArray[0], keyValueArray[1]));
            }
        }
        restoreIntoInMemoryTreeRecords(recoveredRecords);
    }

    private void restoreIntoInMemoryTreeRecords(Collection<Record> recoveredRecords) {
        logRecoveredRecords(recoveredRecords);
        if (!recoveredRecords.isEmpty()) {
            LocalFilePointersManager.getInstance().putAllIntoInMemoryTree(recoveredRecords);
        }
    }

    private Path getPathToTemporaryLogFile() {
        return Path.of(System.getProperty(CacheConfigConstants.TEMPORARY_LOG_LOCATION));
    }

    private void logRecoveredRecords(Collection<Record> recoveredRecords) {
        if (!recoveredRecords.isEmpty()) {
            System.out.printf("Recovered to writable SSTable %d records during parsing log file\n", recoveredRecords.size());
        } else {
            System.out.println("Recovery process do not detect any valid records to be restored");
        }
    }
}
