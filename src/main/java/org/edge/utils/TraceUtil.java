package org.edge.utils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class TraceUtil {

    private static BufferedWriter bufferedWriter;

    public static void init(String traceFilePath) {
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(traceFilePath, true);
        } catch(IOException ex) {
            ex.printStackTrace();
        }

        bufferedWriter = new BufferedWriter(fileWriter);
    }

    public static void trace(String msg) {
        appendTextToFile(msg);
    }

    private static void appendTextToFile(String msg) {
        try {
            bufferedWriter.write(msg);
            bufferedWriter.newLine();
        } catch(IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void endTrace() {
        if(bufferedWriter == null)
            return;
        try {
            bufferedWriter.flush();
            bufferedWriter.close();
        } catch(IOException ex) {
            ex.printStackTrace();
        }
    }
}