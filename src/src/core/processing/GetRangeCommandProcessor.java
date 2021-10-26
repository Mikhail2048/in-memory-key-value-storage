package src.core.processing;

import src.core.InputParser;
import src.core.models.CommandType;
import src.core.models.RangeGetRequest;
import src.files.LocalFilePointersManager;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class GetRangeCommandProcessor implements CommandProcessor {

    private final InputParser inputParser;
    private final LocalFilePointersManager localFilePointersManager;

    private static GetRangeCommandProcessor instance;

    public GetRangeCommandProcessor() {
        this.inputParser = new InputParser();
        localFilePointersManager = LocalFilePointersManager.getInstance();
    }

    /**
     * TODO: get rid of synchronized block
     */
    public synchronized static GetRangeCommandProcessor getInstance() {
        if (instance == null) {
            instance = new GetRangeCommandProcessor();
        }
        return instance;
    }

    @Override
    public boolean checkInputValid(OutputStream outputStream, StringBuffer rawInput) throws IOException {
        final boolean isGetRangeRequestValid = inputParser.isGetRangeRequestValid(rawInput.toString());
        if (!isGetRangeRequestValid) {
            outputStream.write(String.format("ERROR. Range request: '%s' is not valid. Provided range does not make sense\n", rawInput).getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        }
        return true;
    }

    @Override
    public void processCommand(OutputStream outputStream, StringBuffer rawInput) {
        final RangeGetRequest rangeGetRequest = inputParser.extractRangeGetRequest(rawInput.toString());
        final List<String> valuesInRange = localFilePointersManager.getValuesInRange(rangeGetRequest.getFrom(), rangeGetRequest.getTo());
        valuesInRange.forEach(value -> {
            try {
                outputStream.write((value + "\n").getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public CommandType supports() {
        return CommandType.GET_RANGE;
    }
}
