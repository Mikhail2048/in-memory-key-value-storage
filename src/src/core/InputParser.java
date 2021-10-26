package src.core;

import src.core.models.CommandType;
import src.core.models.RangeGetRequest;
import src.core.models.Record;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class InputParser {

    public CommandType extractCommand(String rawInput) {
        final String[] splitInput = rawInput.split(" ");
        if (splitInput[0].equals(CommandType.GET.getCommand())) {
            return splitInput[1].contains("-") ? CommandType.GET_RANGE : CommandType.GET;
        }
        return CommandType.PUT;
    }

    public List<Record> parsePut(String dataOnlyInput) {
        final String[] recordsAsStringArray = dataOnlyInput.split(";");
        return Arrays.stream(recordsAsStringArray)
                .map(s -> s.split(","))
                .map(values -> new Record(values[0].trim(), values[1].trim()))
                .collect(Collectors.toList());
    }

    public String parseGet(String dataOnlyInput) {
        return dataOnlyInput.trim();
    }

    public boolean isValidPutInput(String rawPutCommand) {
        if (rawPutCommand == null || rawPutCommand.length() == 0) return false;
        return rawPutCommand.length() >= 5 &&
                rawPutCommand.contains(",") &&
                rawPutCommand.contains("PUT");
    }

    public RangeGetRequest extractRangeGetRequest(String rawInput) {
        final int indexOfSymbolRightAfterCommand = rawInput.indexOf(CommandType.GET_RANGE.getCommand()) + CommandType.GET_RANGE.getCommand().length();
        final String[] requestedRangeParts = rawInput.substring(indexOfSymbolRightAfterCommand).trim().split("-");
        return new RangeGetRequest(requestedRangeParts[0], requestedRangeParts[1]);
    }

    public boolean isGetRangeRequestValid(String rawInput) {
        final RangeGetRequest rangeGetRequest = this.extractRangeGetRequest(rawInput);
        return rangeGetRequest.getFrom().compareTo(rangeGetRequest.getTo()) < 0;
    }
}
