/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cs.scai.qa.questionclassifier;

import edu.ucla.cs.scai.swim.qa.ontology.QueryConstraint;
import edu.ucla.cs.scai.swim.qa.ontology.QueryModel;
import java.util.ArrayList;

/**
 *
 * @author Giuseppe M. Mazzeo <mazzeo@cs.ucla.edu>
 */
public class QueryResolver {

    public ArrayList<QueryModel> resolveQueries(SyntacticTree tree, ArrayList<PennTreebankPattern> patterns) {
        ArrayList<QueryModel> res = new ArrayList<>();
        for (PennTreebankPattern pattern : patterns) {
            res.addAll(resolveQueries(tree, pattern));
        }
        return res;
    }

    public ArrayList<QueryModel> resolveQueries(SyntacticTree tree, PennTreebankPattern pattern) {
        ArrayList<QueryModel> res = new ArrayList<>();
        for (QueryModel qm : pattern.queryModels) {
            QueryModel mqm = new QueryModel();
            res.add(mqm);
            for (QueryConstraint qc : qm.getConstraints()) {
                QueryConstraint mqc = new QueryConstraint(qc.isOptional());
                mqc.setSubjString(reduceGeneralExpression(qc.getSubjExpr(), tree));
                mqc.setAttrString(reduceGeneralExpression(qc.getAttrExpr(), tree));
                mqc.setValueString(reduceGeneralExpression(qc.getValueExpr(), tree));
                mqm.getConstraints().add(mqc);
            }
        }
        return res;
    }

    private String reduceGeneralExpression(String expr, SyntacticTree tree) {
        String[] exprs = expr.trim().split("\\+");
        String res = "";
        for (String s : exprs) {
            if (res.length() > 0) {
                res += " ";
            }
            res += reduceSingleExpression(s, tree);
        }
        return res;
    }

    private String reduceSingleExpression(String expr, SyntacticTree tree) {
        expr = expr.trim();
        if (expr.startsWith("w(")) {
            expr = expr.replaceFirst("w\\(", "");
            expr = expr.substring(0, expr.length() - 1);
            return tree.labelledNodes.get(expr).getLeafValues();
        } else if (expr.startsWith("l(")) {
            expr = expr.replaceFirst("l\\(", "");
            expr = expr.substring(0, expr.length() - 1);
            return tree.labelledNodes.get(expr).getLeafLemmas();
        } if (expr.startsWith("entities(")) {
            expr = expr.replaceFirst("entities\\(", "");
            expr = expr.substring(0, expr.length() - 1);
            return "entities(\n"+tree.labelledNodes.get(expr).toString()+"\n)";
        }  if (expr.startsWith("values(")) {
            expr = expr.replaceFirst("values\\(", "");
            expr = expr.substring(0, expr.length() - 1);
            return "values(\n"+tree.labelledNodes.get(expr).toString()+"\n)";
        }
        return expr;
    }
}
