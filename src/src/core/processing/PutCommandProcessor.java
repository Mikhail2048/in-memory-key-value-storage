package src.core.processing;

import src.core.InputParser;
import src.core.models.CommandType;
import src.core.models.Record;
import src.files.LocalFilePointersManager;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class PutCommandProcessor implements CommandProcessor {

    private final InputParser inputParser;
    private final LocalFilePointersManager localPointersManager;

    private static PutCommandProcessor instance;

    /**
     * TODO: get rid of synchronized block
     */
    public synchronized static PutCommandProcessor getInstance() {
        if (instance == null) {
            instance = new PutCommandProcessor();
        }
        return instance;
    }

    private PutCommandProcessor() {
        this.localPointersManager = LocalFilePointersManager.getInstance();
        this.inputParser = new InputParser();
    }

    @Override
    public boolean checkInputValid(OutputStream outputStream, StringBuffer rawInput) throws IOException {
        final boolean isValidPutInput = inputParser.isValidPutInput(rawInput.toString());
        if (!isValidPutInput) {
            outputStream.write(String.format("ERROR: Input '%s' is invalid\n", rawInput).getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            return false;
        }
        return true;
    }

    @Override
    public void processCommand(OutputStream outputStream, StringBuffer rawInput) throws IOException {
        final List<Record> records = inputParser.parsePut(getDataInputOnly(rawInput, CommandType.PUT));
        records.forEach(record -> {
            try {
                localPointersManager.putKeyValuePairToLocalTree(record.getKey(), record.getValue());
                System.out.println("Put to local cache key: " + record.getKey() + ", value :" + record.getValue());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        final BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream));
        bufferedWriter.write(String.format("OK %s\n", records.size()));
        bufferedWriter.flush();
    }

    @Override
    public CommandType supports() {
        return CommandType.PUT;
    }
}
