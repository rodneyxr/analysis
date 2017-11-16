package edu.utsa.fileflow.client.fileflow;

import edu.utsa.fileflow.analysis.AnalysisException;
import edu.utsa.fileflow.analysis.Analyzer;
import edu.utsa.fileflow.cfg.FlowPoint;
import edu.utsa.fileflow.client.fileflow.grammar.GrammarAnalysis;
import edu.utsa.fileflow.client.fileflow.grammar.GrammarAnalysisDomain;
import edu.utsa.fileflow.client.fileflow.variable.VariableAnalysis;
import edu.utsa.fileflow.client.fileflow.variable.VariableAnalysisDomain;
import edu.utsa.fileflow.utilities.FileFlowHelper;
import edu.utsa.fileflow.utilities.GraphvizGenerator;

import java.io.File;
import java.io.IOException;

public class FileFlowAnalysisMain {

	public static boolean DEBUG = true;
	private static final String TEST_SCRIPT = "scripts/test.ffa";

	public static void main(String[] args) throws IOException {
		Analyzer.CONTINUE_ON_ERROR = true;
		Analyzer.VERBOSE = false;
		FlowPoint cfg = FileFlowHelper.generateControlFlowGraphFromFile(new File(TEST_SCRIPT));
		writeDOT(cfg);

        /* Variable Analysis */
		VariableAnalysisDomain variableAnalysisDomain = new VariableAnalysisDomain();
		VariableAnalysis variableAnalysis = new VariableAnalysis();
		Analyzer<VariableAnalysisDomain, VariableAnalysis> variableAnalyzer = new Analyzer<>(variableAnalysisDomain, variableAnalysis);

		/* Grammar Analysis */
		GrammarAnalysisDomain grammarDomain = new GrammarAnalysisDomain();
		GrammarAnalysis grammarAnalysis = new GrammarAnalysis();
		Analyzer<GrammarAnalysisDomain, GrammarAnalysis> grammarAnalyzer = new Analyzer<>(grammarDomain, grammarAnalysis);

		/* File Flow Analysis */
		FileFlowAnalysisDomain ffaDomain = new FileFlowAnalysisDomain();
		FileFlowAnalysis ffaAnalysis = new FileFlowAnalysis();
		Analyzer<FileFlowAnalysisDomain, FileFlowAnalysis> ffaAnalyzer = new Analyzer<>(ffaDomain, ffaAnalysis);

		try {
			variableAnalyzer.analyze(cfg);
			grammarAnalyzer.analyze(cfg);
			ffaAnalyzer.analyze(cfg);

			// Run again
			FileFlowAnalysisDomain results = ffaAnalyzer.analyze(cfg);
			GraphvizGenerator.saveDOTToFile(results.post.toDot(), TEST_SCRIPT + ".dot");
		} catch (AnalysisException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Generate DOT file before analysis
	 *
	 * @param cfg FlowPoint to represent entry point of the CFG
	 */
	private static void writeDOT(FlowPoint cfg) {
		String dot = GraphvizGenerator.generateDOT(cfg);
		GraphvizGenerator.saveDOTToFile(dot, TEST_SCRIPT + ".cfg.dot");
		if (DEBUG) {
			System.out.println("CFG file written to: 'dot/" + TEST_SCRIPT + ".cfg.dot'");
			System.out.println();
		}
	}

}
