package com.mulhaq.loganalyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class LogChunker {
    private static final int CHUNK_SIZE = 3000;

    /**
     * Read a log file and split it into chunks of maximum CHUNK_SIZE characters.
     * Each chunk represents a piece of log data to be analyzed.
     */
    public static List<String> chunkLogFile(String filePath) throws IOException {
        String content = Files.readString(Paths.get(filePath));
        List<String> chunks = new ArrayList<>();
        
        for (int i = 0; i < content.length(); i += CHUNK_SIZE) {
            int end = Math.min(i + CHUNK_SIZE, content.length());
            chunks.add(content.substring(i, end));
        }
        
        return chunks;
    }

    /**
     * Get the file size for information purposes.
     */
    public static long getFileSizeBytes(String filePath) throws IOException {
        return Files.size(Paths.get(filePath));
    }
}
