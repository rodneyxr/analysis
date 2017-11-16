package edu.utsa.fileflow.client.fileflow;

import edu.utsa.fileflow.analysis.Analysis;
import edu.utsa.fileflow.analysis.AnalysisException;
import edu.utsa.fileflow.analysis.Analyzer;
import edu.utsa.fileflow.antlr.FileFlowParser.FunctionCallContext;
import edu.utsa.fileflow.antlr.FileFlowParser.ValueContext;
import edu.utsa.fileflow.cfg.FlowPoint;
import edu.utsa.fileflow.cfg.FlowPointContext;
import edu.utsa.fileflow.client.AssignContext;
import edu.utsa.fileflow.client.fileflow.grammar.GrammarAnalysisDomain;
import edu.utsa.fileflow.client.fileflow.variable.VariableAnalysisDomain;
import edu.utsa.fileflow.utilities.GraphvizGenerator;

public class FileFlowAnalysis extends Analysis<FileFlowAnalysisDomain> {

	// Previous analysis domains
	private GrammarAnalysisDomain gDomain;
	private VariableAnalysisDomain vDomain;

	// Initial file structure
	private FileStructure lastInit = null;
	private int runCounter = 0;

	@Override
	public FileFlowAnalysisDomain onBegin(FileFlowAnalysisDomain domain, FlowPoint flowPoint) throws AnalysisException {
		System.out.println("\n***** Run: " + runCounter);
		if (runCounter > 0) {
			domain.post = lastInit;
			flowPoint.setDomain(domain);
		}
		return super.onBegin(domain, flowPoint);
	}

	@Override
	public FileFlowAnalysisDomain onFinish(FileFlowAnalysisDomain domain) throws AnalysisException {
		// Write post and init to DOT file
		GraphvizGenerator.saveDOTToFile(domain.init.toDot(), "debug/init" + runCounter + ".dot");
		GraphvizGenerator.saveDOTToFile(domain.post.toDot(), "debug/post" + runCounter + ".dot");
		lastInit = domain.init.clone();
		runCounter++;
		return super.onFinish(domain);
	}

	@Override
	public FileFlowAnalysisDomain onBefore(FileFlowAnalysisDomain domain, FlowPointContext context) throws AnalysisException {
		gDomain = (GrammarAnalysisDomain) context.getFlowPoint().getDomain(GrammarAnalysisDomain.class);
		vDomain = (VariableAnalysisDomain) context.getFlowPoint().getDomain(VariableAnalysisDomain.class);
		return super.onBefore(domain, context);
	}

	@Override
	public FileFlowAnalysisDomain touch(FileFlowAnalysisDomain domain, FlowPointContext context) throws AnalysisException {
		VariableAutomaton va = getValue(domain, context, 0);

		// add the automaton to the file structure
		try {
			domain.post.createFile(va);
		} catch (FileStructureException e) {
			if (Analyzer.CONTINUE_ON_ERROR && runCounter == 0) {
				if (e.getMessage().contains("No such file or directory")) {
					domain.init.forceCreate(va.getParentDirectory());
				}
			}
			throw new AnalysisException(e.getMessage());
		}

		return domain;
	}

	@Override
	public FileFlowAnalysisDomain mkdir(FileFlowAnalysisDomain domain, FlowPointContext context) throws AnalysisException {
		VariableAutomaton va = getValue(domain, context, 0);

		// add the automaton to the file structure
		try {
			domain.post.createDirectory(va);
		} catch (FileStructureException e) {
			if (Analyzer.CONTINUE_ON_ERROR && runCounter == 0) {
				domain.init.forceCreate(va);
				System.out.println(e.getMessage());
			}
			throw new AnalysisException(e.getMessage());
		}

		return domain;
	}

	@Override
	public FileFlowAnalysisDomain copy(FileFlowAnalysisDomain domain, FlowPointContext context) throws AnalysisException {
		VariableAutomaton v1 = getValue(domain, context, 0);
		VariableAutomaton v2 = getValue(domain, context, 1);

		try {
			domain.post.copy(v1, v2);
		} catch (FileStructureException e) {
			if (Analyzer.CONTINUE_ON_ERROR && runCounter == 0) {
				// Create v1 or v2 or both?
				if (!domain.post.fileExists(v1))
					domain.init.forceCreate(v1);
				if (!domain.post.fileExists(v2))
					domain.init.forceCreate(v2);
			}
			throw new AnalysisException(e.getMessage());
		}

		return domain;
	}

	@Override
	public FileFlowAnalysisDomain remove(FileFlowAnalysisDomain domain, FlowPointContext context) throws AnalysisException {
		VariableAutomaton va = getValue(domain, context, 0);

		// remove the automaton from the file structure
		try {
			domain.post.removeFile(va);
		} catch (FileStructureException e) {
			if (Analyzer.CONTINUE_ON_ERROR && runCounter == 0) {
				if (!e.getMessage().endsWith("recursive option")) {
					domain.init.forceCreate(va);
				}
			}
			throw new AnalysisException(e.getMessage());
		}

		return domain;
	}

	@Override
	public FileFlowAnalysisDomain removeRecursive(FileFlowAnalysisDomain domain, FlowPointContext context) throws AnalysisException {
		VariableAutomaton va = getValue(domain, context, 0);

		// remove the automaton from the file structure
		try {
			domain.post.removeFileRecursive(va);
		} catch (FileStructureException e) {
			if (Analyzer.CONTINUE_ON_ERROR && runCounter == 0) {
				domain.init.forceCreate(va);
			}
			throw new AnalysisException(e.getMessage());
		}

		return domain;
	}

	@Override
	public FileFlowAnalysisDomain assertFunc(FileFlowAnalysisDomain domain, FlowPointContext context) throws AnalysisException {
		FunctionCallContext ctx = (FunctionCallContext) context.getContext();
		String s1 = null, s2 = null;
		if (ctx.condition().children.size() == 2) {
			s1 = ctx.condition().getChild(0).getText();
			s2 = ctx.condition().getChild(1).getText();
		}
		if (s1 == null || s2 == null) {
			System.err.println("Invalid command");
			return domain;
		}

		if (s1.equals("exists")) {
			VariableAutomaton va = domain.table.get(s2);
			boolean exists = domain.post.fileExists(va);
			if (!exists) {
				System.out.printf("WARNING: '%s**' does not exist\n", va);
			}
		} else if (s1.equals("!") && s2.startsWith("exists")) {
			String b = ctx.condition().getChild(1).getChild(1).getText();
			VariableAutomaton va = domain.table.get(b);
			boolean exists = domain.post.fileExists(va);
			if (exists) {
				System.out.printf("WARNING: '%s**' exists\n", va);
			}
		} else {
			System.err.println("Invalid assertion.");
			return domain;
		}
		return domain;
	}

	@Override
	public FileFlowAnalysisDomain enterAssignment(FileFlowAnalysisDomain domain, FlowPointContext context) throws AnalysisException {
		AssignContext ctx = new AssignContext(context);
		domain.table.put(ctx.var0, new VariableAutomaton(gDomain.getVariable(ctx.var0, vDomain.getLiveVariables())));
		// TODO: handle arrays and user INPUT
		return super.enterAssignment(domain, context);
	}

	/**
	 * Gets the nth parameter of a function call.
	 */
	private VariableAutomaton getValue(FileFlowAnalysisDomain domain, FlowPointContext context, int n) {
		FunctionCallContext ctx = (FunctionCallContext) context.getContext();
		ValueContext v = ctx.value(n);

		// get the variable from the symbol table or create a new one
		if (v.Variable() != null) // if v is a variable
			return domain.table.get(v.Variable().getText());

		// v is a string literal
		return new VariableAutomaton(v.String().getText());
	}

}
