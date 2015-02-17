/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cs.scai.qa.questionclassifier;

import edu.ucla.cs.scai.swim.qa.ontology.QueryConstraint;
import edu.ucla.cs.scai.swim.qa.ontology.QueryModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;

/**
 *
 * @author Giuseppe M. Mazzeo <mazzeo@cs.ucla.edu>
 */
public class PennTreebankPattern {

    String name;
    PennTreebankPatternNode root;
    ArrayList<QueryModel> queryModels = new ArrayList<>();

    public PennTreebankPattern(String name, String stringPattern) throws Exception {
        this.name = name;
        String treeStringPattern = "";
        String[] lines = stringPattern.split("\n");
        int i = 0;
        while (i < lines.length && !lines[i].equals("")) {
            treeStringPattern += lines[i];
            i++;
        }

        String[] tokens = treeStringPattern.replaceAll(" ", "").split("(?<=\\))|(?=\\))|(?<=\\()|(?=\\()");
        int[] currentPosition = new int[1];
        root = new PennTreebankPatternNode(tokens, currentPosition);

        while (i < lines.length) {
            if (lines[i].equals("")) {
                i++;
                continue;
            }
            String queryStringPattern = "";
            while (i < lines.length && !lines[i].equals("")) {
                queryStringPattern += lines[i];
                i++;
            }
            queryModels.add(buildQueryModel(queryStringPattern));
        }
    }

    private QueryModel buildQueryModel(String queryStringPattern) throws Exception {
        QueryModel qm = new QueryModel();
        for (String constraint : queryStringPattern.trim().split("\\.")) {
            constraint = constraint.trim().toLowerCase();
            boolean optional = false;
            if (constraint.startsWith("optional")) {
                optional = true;
                constraint = constraint.replaceFirst("optional", "").trim();
            }
            String[] exprs = constraint.split(" ");
            if (exprs.length > 1) {
                QueryConstraint qc = new QueryConstraint(exprs[0], exprs[1], exprs[2], optional);
                qm.getConstraints().add(qc);
            } else {
                QueryConstraint qc = new QueryConstraint(exprs[0], "", "", optional);
                qm.getConstraints().add(qc);
            }

        }
        return qm;
    }

    public void print() {
        root.print(0);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + Objects.hashCode(this.name);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PennTreebankPattern other = (PennTreebankPattern) obj;
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "PennTreebankPattern{" + "name=" + name + ", root=" + root + '}';
    }

}
