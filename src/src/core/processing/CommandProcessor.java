package src.core.processing;

import src.core.models.CommandType;

import java.io.IOException;
import java.io.OutputStream;

public interface CommandProcessor {

    /**
     * @return true if input is valid, false otherwise
     */
    boolean checkInputValid(OutputStream outputStream, StringBuffer rawInput) throws IOException;

    void processCommand(OutputStream outputStream, StringBuffer rawInput) throws IOException;

    CommandType supports();

    default String getDataInputOnly(StringBuffer rawInput, CommandType commandType) {
        final int start = rawInput.indexOf(commandType.getCommand());
        return rawInput.delete(start, start + 3).toString().trim();
    }
}
