package com.bitrot;

import java.nio.file.Path;

/**
 * A utility class to remove a file from the file_verification SQLite database.
 * This is useful when you want to force a file to be re-verified in the next run.
 */
public class ClearFileFromRecencyDB {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java -cp your-jar.jar com.bitrot.ClearFileFromDB <absoluteFilePath>");
            System.exit(1);
        }

        final String absoluteFilePath = args[0];
        System.out.println("Attempting to remove file from verification database: " + absoluteFilePath);

        SkipUtil skipUtil = null;
        try {
            skipUtil = new SkipUtil();
            int rowsAffected = skipUtil.removeFileFromDatabase(Path.of(absoluteFilePath));
            if (rowsAffected > 0) {
                System.out.println("Successfully removed file from verification database: " + absoluteFilePath);
            } else {
                System.out.println("File not found in verification database: " + absoluteFilePath);
            }
        } catch (Exception e) {
            System.err.println("Error removing file from verification database: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (skipUtil != null) {
                try {
                    skipUtil.close();
                } catch (Exception e) {
                    System.err.println("Error closing SkipUtil: " + e.getMessage());
                }
            }
        }
    }
}
