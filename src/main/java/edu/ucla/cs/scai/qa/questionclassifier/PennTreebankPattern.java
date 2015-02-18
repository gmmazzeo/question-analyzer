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
    ArrayList<IQueryModel> iQueryModels = new ArrayList<>();

    public PennTreebankPattern(String name, String stringPattern) throws Exception {
        this.name = name;
        String treeStringPattern = "";
        String[] lines = stringPattern.split("\n");
        int i = 0;
        while (i < lines.length && !lines[i].equals("")) {
            if (!lines[i].startsWith("%")) {
                treeStringPattern += lines[i];
            }
            i++;
        }

        if (name.equals("WH_BE_NP")) {
            System.out.print("");
        }
        String[] tokens = treeStringPattern.replaceAll(" ", "").split("(?<=\\))|(?=\\))|(?<=\\()|(?=\\()|(?=\\^)");
        int[] currentPosition = new int[1];
        root = new PennTreebankPatternNode(tokens, currentPosition);

        while (i < lines.length) {
            if (lines[i].equals("") || lines[i].startsWith("%")) {
                i++;
                continue;
            }
            String queryStringPattern = "";
            while (i < lines.length && !lines[i].equals("")) {
                queryStringPattern += lines[i];
                i++;
            }
            iQueryModels.add(buildQueryModel(queryStringPattern));
        }
    }

    private IQueryModel buildQueryModel(String queryStringPattern) throws Exception {
        IQueryModel qm = new IQueryModel();
        for (String constraint : queryStringPattern.trim().split("\\.")) {
            constraint = constraint.trim().toLowerCase();
            boolean optional = false;
            if (constraint.startsWith("optional")) {
                optional = true;
                constraint = constraint.replaceFirst("optional", "").trim();
            }
            String[] exprs = constraint.split(" ");
            if (exprs.length > 1) {

            } else {
                if (exprs[0].startsWith("values(")) {
                    exprs[0]=exprs[0].replaceAll("values\\(", "");
                    exprs[0]=exprs[0].substring(0, exprs[0].length()-1);
                    exprs=exprs[0].split(",");
                    if (exprs.length==2) {
                        qm.constraints.add(values(exprs[0].trim(), exprs[1].trim(), optional));
                    } else if (exprs.length==3) {
                        qm.constraints.add(values(exprs[0].trim(), exprs[1].trim(), exprs[2].trim(), optional));
                    } else {
                        throw new Exception("Wrong query model in pattern "+name);
                    }
                }
            }

        }
        return qm;
    }

    public IValueQueryConstraint values(String entityExpression, String attributeExpression, String valueVariableName, boolean optional) {        
        return new IValueQueryConstraint(entityExpression, attributeExpression, valueVariableName, optional);
    }
    
    public IValueQueryConstraint values(String valueExpression, String valueVariableName, boolean optional) {
        return new IValueQueryConstraint(valueExpression, valueVariableName, optional);
    }    
    
    public IEntityQueryConstraint entities(String entityVariableName, String attributeExpression, String valueExpression, boolean optional) {        
        return new IEntityQueryConstraint(entityVariableName, attributeExpression, valueExpression, optional);
    }
    
    public IEntityQueryConstraint entities(String entityExpression, String entityVariableName, boolean optional) {
        return new IEntityQueryConstraint(entityExpression, entityVariableName, optional);
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
