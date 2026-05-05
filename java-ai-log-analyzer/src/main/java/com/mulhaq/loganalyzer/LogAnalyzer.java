package com.mulhaq.loganalyzer;

import java.io.IOException;
import java.util.List;

public class LogAnalyzer {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java -jar java-ai-log-analyzer-1.0.jar <log-file-path>");
            System.exit(1);
        }

        String logFilePath = args[0];
        String apiKey = System.getenv("GROQ_API_KEY");
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("Error: GROQ_API_KEY environment variable is not set");
            System.exit(1);
        }

        try {
            System.out.println("=== AI-Powered Log Analyzer ===\n");
            System.out.println("Log file: " + logFilePath);
            
            long fileSizeBytes = LogChunker.getFileSizeBytes(logFilePath);
            System.out.println("File size: " + formatBytes(fileSizeBytes) + "\n");

            // Read and chunk the log file
            System.out.println("Reading and chunking log file...");
            List<String> chunks = LogChunker.chunkLogFile(logFilePath);
            System.out.println("Total chunks: " + chunks.size() + "\n");

            // Initialize Groq client
            GroqClient groqClient = new GroqClient(apiKey);

            // Analyze each chunk
            int chunkCount = 1;
            for (String chunk : chunks) {
                System.out.println("--- Chunk " + chunkCount + " of " + chunks.size() + " ---");
                
                try {
                    // Send to Groq API
                    String analysisResponse = groqClient.analyzeLogChunk(chunk);
                    
                    // Parse response into structured format
                    AnalysisResult result = groqClient.parseAnalysisResponse(analysisResponse);
                    
                    // Print results
                    printAnalysisResult(result, chunkCount);
                    
                } catch (Exception e) {
                    System.err.println("Error analyzing chunk " + chunkCount + ": " + e.getMessage());
                    e.printStackTrace();
                }
                
                System.out.println();
                chunkCount++;
            }

            System.out.println("=== Analysis Complete ===");

        } catch (IOException e) {
            System.err.println("Error reading log file: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Print a formatted analysis result to console.
     */
    private static void printAnalysisResult(AnalysisResult result, int chunkNumber) {
        System.out.println("Root Cause: " + result.getRootCause());
        System.out.println("Severity: " + result.getSeverity());
        
        if (result.getAffectedComponents() != null && !result.getAffectedComponents().isEmpty()) {
            System.out.println("Affected Components:");
            for (String component : result.getAffectedComponents()) {
                System.out.println("  - " + component);
            }
        }
        
        if (result.getFixSuggestions() != null && !result.getFixSuggestions().isEmpty()) {
            System.out.println("Fix Suggestions:");
            for (String suggestion : result.getFixSuggestions()) {
                System.out.println("  - " + suggestion);
            }
        }
        
        if (result.getSummary() != null && !result.getSummary().isEmpty()) {
            System.out.println("Summary: " + result.getSummary());
        }
    }

    /**
     * Format bytes into human-readable format.
     */
    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.2f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
