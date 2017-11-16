package edu.utsa.fileflow.client.fileflow.grammar;

import dk.brics.automaton.Automaton;
import edu.utsa.fileflow.analysis.Analysis;
import edu.utsa.fileflow.analysis.AnalysisException;
import edu.utsa.fileflow.cfg.FlowPointContext;
import edu.utsa.fileflow.client.AssignContext;
import edu.utsa.fileflow.client.fileflow.FileFlowAnalysisMain;
import edu.utsa.fileflow.client.fileflow.variable.Variable;
import edu.utsa.fileflow.client.fileflow.variable.VariableAnalysisDomain;

import java.util.Set;

/**
 * This class uses a live variable set to create a grammar for file flow analysis.
 * <p>
 * Created by Rodney on 3/27/2017.
 */
public class GrammarAnalysis extends Analysis<GrammarAnalysisDomain> {

	private VariableAnalysisDomain vDomain;

	@Override
	public GrammarAnalysisDomain onBefore(GrammarAnalysisDomain domain, FlowPointContext context) throws AnalysisException {
		vDomain = (VariableAnalysisDomain) context.getFlowPoint().getOriginalDomain(VariableAnalysisDomain.class);
		return super.onBefore(domain, context);
	}

	@Override
	public GrammarAnalysisDomain onFinish(GrammarAnalysisDomain domain) throws AnalysisException {
		if (FileFlowAnalysisMain.DEBUG) {
			System.out.println("***** Grammar Analysis Results:");
			System.out.println(domain.grammar);
		}
		return super.onFinish(domain);
	}

	/**
	 * Supported Operations:
	 * var = var
	 * var = var.var
	 * var = '$literal'
	 */
	@Override
	public GrammarAnalysisDomain enterAssignment(GrammarAnalysisDomain domain, FlowPointContext context) throws AnalysisException {
		AssignContext ctx = new AssignContext(context);
		int flowpointID = context.getFlowPoint().id;
		Variable v0 = new Variable(ctx.var0, flowpointID);

		// check if this node has been visited already
		if (domain.grammar.visited.contains(flowpointID)) {
			return domain;
		}

		domain.grammar.addNonterminal(v0);

		// automaton production: $x0 = 'a';
		if (ctx.literal != null) {
			domain.grammar.addAutomatonProduction(v0, Automaton.makeString(ctx.literal));
		} else if (ctx.var1 != null) {
			// get or create v1
			Set<Variable> v1Set = vDomain.getLiveVariables().getVariable(ctx.var1);
			if (v1Set == null) {
				throw new AnalysisException(String.format("%s is not defined", ctx.var1));
			}

			for (Variable v1 : v1Set) {
				if (ctx.var2 != null) {
					// pair production: $x0 = $x1.$x2;
					Set<Variable> v2Set = vDomain.getLiveVariables().getVariable(ctx.var2);
					if (v2Set == null)
						throw new AnalysisException(String.format("%s is not defined", ctx.var2));
					for (Variable v2 : v2Set) {
						if (!v1.name.equals(v2.name) || v1.id == v2.id) {
							domain.grammar.addPairProduction(v0, v1, v2);
						}
					}
				} else {
					// unit production: $x0 = $x1;
					domain.grammar.addUnitProduction(v0, v1);
				}
			}
		}

		return domain;
	}
}
