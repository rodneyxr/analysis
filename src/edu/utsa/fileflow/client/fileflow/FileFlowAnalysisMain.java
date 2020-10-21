package edu.utsa.fileflow.client.fileflow;

import edu.utsa.fileflow.analysis.Analyzer;
import edu.utsa.fileflow.cfg.FlowPoint;
import edu.utsa.fileflow.utilities.FileFlowHelper;
import edu.utsa.fileflow.utilities.GraphvizGenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class FileFlowAnalysisMain {
    private static boolean DEBUG = true;

    public static void main(String[] args) {
        Analyzer.CONTINUE_ON_ERROR = true;
        Analyzer.VERBOSE = false;

        // Check arguments
        if (args.length != 1) {
            System.err.println("usage: prog <file/directory>");
            System.exit(1);
        }

        // Create the logger
        Logger logger = Logger.getLogger("FFA");
        FileHandler handler;

        File file = new File(args[0]);
        File[] files;
        if (file.isDirectory()) {
            files = file.listFiles();
        } else {
            files = new File[]{file};
        }

        // Make sure files were found
        if (files == null) {
            System.err.println("no '.ffa' files were found");
            System.exit(1);
        }

        // Set up the main logger
        Logger mainLogger = Logger.getLogger("FFA_MAIN");
        FileHandler mainHandler = null;

        try {
            mainHandler = new FileHandler("main.log");
            mainLogger.addHandler(mainHandler);
            mainLogger.setUseParentHandlers(false);
            mainHandler.setFormatter(new MySimpleFormatter());
        } catch (IOException e) {
            System.err.println("failed to create main.log");
            System.exit(1);
        }

        for (File f : files) {
            if (f.toPath().toString().endsWith(".ffa")) {
                String saveDir = f.toPath().getFileName().toString().replaceAll("\\.ffa$", "");
                String dotSaveDir = Paths.get("dot", saveDir).toString();
                String handlerPath = Paths.get(dotSaveDir, "debug.log").toString();

                // Create the directory to save the log and dot files in
                try {
                    new File(handlerPath).getParentFile().mkdirs();
                } catch (Exception e) {
                    System.err.printf("failed to create directory: %s\n", dotSaveDir);
                    continue;
                }

                // Create the log file handle
                try {
                    handler = new FileHandler(handlerPath);
                } catch (IOException e) {
                    System.err.printf("failed to create log file: %s\n%s\n", handlerPath, e.getMessage());
                    continue;
                }

                // Set up logging
                logger.addHandler(handler);
                logger.setUseParentHandlers(false); // Remove logging from console
                handler.setFormatter(new MySimpleFormatter());
                logger.info(f.toString());

                try {
                    FlowPoint cfg = FileFlowHelper.generateControlFlowGraphFromFile(f);
                    writeDOT(cfg, saveDir);
                    System.out.println("\n\n==============================================================================");
                    System.out.println(saveDir);
                    FFA ffa = new FFA(cfg);
                    GraphvizGenerator.PATH_PREFIX = saveDir;
                    ffa.run();
                    GraphvizGenerator.PATH_PREFIX = "";

                    String timeResults = String.format("Variable analysis elapsed time: %dms\n", ffa.variableElapsedTime) +
                            String.format("Grammar analysis elapsed time: %dms\n", ffa.grammarElapsedTime) +
                            String.format("FFA first run elapsed time: %dms\n", ffa.ffaElapsedTime1) +
                            String.format("FFA second run elapsed time: %dms\n", ffa.ffaElapsedTime2);
                    Files.write(Paths.get("dot", saveDir, "time.txt"), timeResults.getBytes());
                    if (FileFlowAnalysisMain.DEBUG)
                        System.out.println(timeResults);
                } catch (Exception e) {
                    mainLogger.warning("failed to analyze: " + f);
                    System.err.println("error: failed to analyze " + f);
                } finally {
                    logger.removeHandler(handler);
                    handler.close();
                }
            }
        }
        mainHandler.close();
    }

    /**
     * Generate DOT file before analysis
     *
     * @param cfg FlowPoint to represent entry point of the CFG
     */
    private static void writeDOT(FlowPoint cfg, String filepath) {
        String dot = GraphvizGenerator.generateDOT(cfg);
        Path path = Paths.get("dot", filepath);
        path.toFile().mkdirs();
        GraphvizGenerator.saveDOTToFile(dot, Paths.get(filepath, "cfg.dot").toString());
        if (FileFlowAnalysisMain.DEBUG) {
            System.out.println("DOT file written to: " + Paths.get(path.toString(), "cfg.dot"));
            System.out.println();
        }
    }


    private static class MySimpleFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            return String.format("%s: %s\n", record.getLevel(), record.getMessage());
        }
    }
}