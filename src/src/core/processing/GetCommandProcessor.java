package src.core.processing;

import src.core.InputParser;
import src.core.models.CommandType;
import src.files.LocalFilePointersManager;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class GetCommandProcessor implements CommandProcessor {

    private final InputParser inputParser;
    private final LocalFilePointersManager localFilePointersManager;

    private static GetCommandProcessor instance;

    /**
     * TODO: get rid of synchronized block
     */
    public synchronized static GetCommandProcessor getInstance() {
       if (instance == null) {
           instance = new GetCommandProcessor();
       }
       return instance;
    }

    private GetCommandProcessor() {
        inputParser = new InputParser();
        localFilePointersManager = LocalFilePointersManager.getInstance();
    }

    @Override
    public boolean checkInputValid(OutputStream outputStream, StringBuffer rawInput) {
        return true; //No input validation check for now
    }

    @Override
    public void processCommand(OutputStream outputStream, StringBuffer rawInput) throws IOException {
        final String key = inputParser.parseGet(getDataInputOnly(rawInput, CommandType.GET));
        final String value = localFilePointersManager.getValueForKey(key);
        try {
            if (value == null) {
                outputStream.write("NULL\n".getBytes(StandardCharsets.UTF_8));
            } else {
                outputStream.write((value + "\n").getBytes(StandardCharsets.UTF_8));
            }
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public CommandType supports() {
        return CommandType.GET;
    }
}