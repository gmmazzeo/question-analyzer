/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cs.scai.qa.questionclassifier;

import edu.ucla.cs.scai.swim.qa.ontology.QueryConstraint;
import edu.ucla.cs.scai.swim.qa.ontology.QueryModel;
import edu.ucla.cs.scai.swim.qa.ontology.SpecificCategoryConstraint;
import edu.ucla.cs.scai.swim.qa.ontology.SpecificEntityConstraint;
import java.util.ArrayList;

/**
 *
 * @author Giuseppe M. Mazzeo <mazzeo@cs.ucla.edu>
 */
public class QueryResolver {

    private int entityCounter;

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
        }
        if (expr.startsWith("entities(")) {
            expr = expr.replaceFirst("entities\\(", "");
            expr = expr.substring(0, expr.length() - 1);
            return "entities(\n" + tree.labelledNodes.get(expr).toString() + "\n)";
        }
        if (expr.startsWith("values(")) {
            expr = expr.replaceFirst("values\\(", "");
            expr = expr.substring(0, expr.length() - 1);
            return "values(\n" + tree.labelledNodes.get(expr).toString() + "\n)";
        }
        return expr;
    }

    public ArrayList<QueryConstraint> entities(SyntacticTreeNode node, String entityVariableName) {
        ArrayList<QueryConstraint> res = new ArrayList<>();
        if (node.npSimple) {
            QueryConstraint qc = new SpecificEntityConstraint(entityVariableName, node.getLeafValues());
            res.add(qc);
        } else if (node.npCompound) {
            QueryConstraint qc = new SpecificCategoryConstraint(entityVariableName, node.getLeafLemmas());
            res.add(qc);
            for (SyntacticTreeNode c : node.children) {
                if (c.value.equals("PP")) {
                    res.addAll(solvePPConstraints(c, entityVariableName));
                } else if (c.value.equals("VP")) {
                    res.addAll(solveVPConstraints(c, entityVariableName));
                }
            }
        }
        return res;
    }

    public ArrayList<QueryConstraint> solvePPConstraints(SyntacticTreeNode node, String entityVariableName) {
        ArrayList<QueryConstraint> res = new ArrayList<>();
        return res;
    }
    
    public ArrayList<QueryConstraint> solveVPConstraints(SyntacticTreeNode node, String entityVariableName) {
        ArrayList<QueryConstraint> res = new ArrayList<>();
        return res;
    }    

    private String getNextEntityVariableName() {
        String res = "e" + entityCounter;
        entityCounter++;
        return res;
    }

}
