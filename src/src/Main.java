package src;

import src.core.config.CacheConfigConstants;
import src.core.config.ConfigurationFileParser;
import src.core.config.InMemoryMapPopulator;
import src.files.FileSegmentsManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

public class Main {

    public static final boolean IS_RUNNING = true;
    private static final InputParser inputParser = new InputParser();
    private static ConcurrentMap<String, String> localCache;
    private static ConcurrentMap<String, Long> localCacheMemoryOffset;
    private static BufferedReader bufferedReader;
    private static OutputStream outputStream;

    public static void main(String[] args) throws IOException {
        new ConfigurationFileParser().parseConfiguration();
        localCache = new InMemoryMapPopulator().populateLocalCacheWithDataFromDirectory();
        new FileSegmentsManager().triggerWatcherThread();
        try (final ServerSocket serverSocket = new ServerSocket(4421)) {
            while (IS_RUNNING) {
                final Socket socket = serverSocket.accept();
                serveOpenedConnection(socket);
            }
        }
    }

    private static void serveOpenedConnection(Socket socket) throws IOException {
        bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        outputStream = socket.getOutputStream();
        StringBuffer rawInput = new StringBuffer();
        while (IS_RUNNING) {
            rawInput.append(bufferedReader.readLine());
            System.out.println("INPUT : " + rawInput);
            if (rawInput.toString().length() >= 5) {
                final CommandType commandType = inputParser.extractCommand(rawInput.toString());
                if (commandType.equals(CommandType.GET)) {
                    processGetAndRetrieveResponse(rawInput);
                } else {
                    processPut(socket, rawInput);
                }
            }
            rawInput = new StringBuffer();
        }
    }

    private static void processPut(Socket socket, StringBuffer rawInput) throws IOException {
        final List<Record> records = inputParser.parsePut(getDataInputOnly(rawInput, CommandType.PUT));
        records.forEach(record -> {
            try {
                Files.write(
                        Paths.get(URI.create("file:///" + System.getProperty(CacheConfigConstants.DATA_DIRECTORY_LOCATION) + File.separator + System.getProperty(CacheConfigConstants.CURRENT_LOG_FILE_NAME))),
                        (record.getKey() + ":" + record.getValue()+"\n").getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.APPEND
                );
                localCache.put(record.getKey(), record.getValue());
                System.out.println("Put localCache " + record.getKey() + ":" + record.getValue());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        final BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        bufferedWriter.write(String.format("OK %s\n", records.size()));
        bufferedWriter.flush();
    }

    private static void processGetAndRetrieveResponse(StringBuffer rawInput) {
        final String key = inputParser.parseGet(getDataInputOnly(rawInput, CommandType.GET));
        final String value = localCache.get(key);
        try {
            if (value == null) {
                outputStream.write("NIL\n".getBytes(StandardCharsets.UTF_8));
            } else {
                outputStream.write((value + "\n").getBytes(StandardCharsets.UTF_8));
            }
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String getDataInputOnly(StringBuffer rawInput, CommandType commandType) {
        final int start = rawInput.indexOf(commandType.command);
        return rawInput.delete(start, start + 3).toString().trim();
    }
}