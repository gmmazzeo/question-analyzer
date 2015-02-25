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
        ArrayList<String> nonCommentLines = new ArrayList<>();
        for (String l : lines) {
            if (!l.startsWith("%")) {
                nonCommentLines.add(l);
            }
        }
        lines = new String[nonCommentLines.size()];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = nonCommentLines.get(i);
        }
        int i = 0;
        while (i < lines.length && !lines[i].equals("")) {
            treeStringPattern += lines[i];
            i++;
        }

        String[] tokens = treeStringPattern.replaceAll(" ", "").split("(?<=\\))|(?=\\))|(?<=\\()|(?=\\()|(?=\\^)|(?<=\\^)");
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
            try {
                iQueryModels.add(buildQueryModel(queryStringPattern));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private IQueryModel buildQueryModel(String queryStringPattern) throws Exception {
        IQueryModel qm = new IQueryModel();
        for (String constraint : queryStringPattern.trim().split("\\.")) {
            constraint = constraint.trim().toLowerCase();
            boolean optional = false;
            if (constraint.startsWith("optional ")) {
                optional = true;
                constraint = constraint.replaceFirst("optional ", "").trim();
            }
            if (constraint.startsWith("values(")) {
                constraint = constraint.replaceAll("values\\(", "");
                constraint = constraint.substring(0, constraint.length() - 1);
                String[] exprs = constraint.split(",");
                if (exprs.length == 3) {
                    qm.constraints.add(nodeValues(exprs[0].trim(), exprs[1].trim(), exprs[2].trim(), "", optional));
                } else if (exprs.length == 4) {
                    qm.constraints.add(nodeValues(exprs[0].trim(), exprs[1].trim(), exprs[2].trim(), exprs[3].trim(), optional));
                } else {
                    throw new Exception("Wrong query model in pattern " + name);
                }
            } else if (constraint.startsWith("entities(")) {
                constraint = constraint.replaceAll("entities\\(", "");
                constraint = constraint.substring(0, constraint.length() - 1);
                String[] exprs = constraint.split(",");
                if (exprs.length == 2) {
                    qm.constraints.add(nodeEntities(exprs[0].trim(), exprs[1].trim(), true, true, optional));
                } else if (exprs.length == 4) {
                    qm.constraints.add(nodeEntities(exprs[0].trim(), exprs[1].trim(), exprs[2].trim().equals("1"), exprs[3].trim().equals("1"), optional));
                } else {
                    throw new Exception("Wrong query model in pattern " + name);
                }
            } else if (constraint.startsWith("siblingconstraints(")) {
                constraint = constraint.replaceAll("siblingconstraints\\(", "");
                constraint = constraint.substring(0, constraint.length() - 1);
                String[] exprs = constraint.split(",");
                if (exprs.length == 2) {
                    qm.constraints.add(siblingConstraints(exprs[0].trim(), exprs[1].trim(), optional));
                } else {
                    throw new Exception("Wrong query model in pattern " + name);
                }
            } else if (constraint.startsWith("boundthroughattribute(")) {
                constraint = constraint.replaceAll("boundthroughattribute\\(", "");
                constraint = constraint.substring(0, constraint.length() - 1);
                String[] exprs = constraint.split(",");
                if (exprs.length == 3) {
                    qm.constraints.add(boundThroughAttribute(exprs[0].trim(), exprs[1].trim(), exprs[2].trim(), null));
                } else if (exprs.length == 4) {
                    qm.constraints.add(boundThroughAttribute(exprs[0].trim(), exprs[1].trim(), exprs[2].trim(), exprs[3].trim()));
                } else {
                    throw new Exception("Wrong query model in pattern " + name);
                }
            } else if (constraint.startsWith("optionalcategory(")) {
                constraint = constraint.replaceAll("optionalcategory\\(", "");
                constraint = constraint.substring(0, constraint.length() - 1);
                String[] exprs = constraint.split(",");
                if (exprs.length == 2) {
                    qm.constraints.add(optionalCategory(exprs[0].trim(), exprs[1].trim()));
                } else {
                    throw new Exception("Wrong query model in pattern " + name);
                }
            } else {
                throw new Exception("Wrong query model in pattern " + name);
            }

        }
        return qm;
    }

    public IValueQueryConstraint values(String entityExpression, String attributeExpression, String valueVariableName, boolean optional) {
        return new IValueQueryConstraint(entityExpression, attributeExpression, valueVariableName, optional);
    }

    public IValueNodeQueryConstraint nodeValues(String nodeLabel, String entityVariableName, String valueVariableName, String attributePrefix, boolean optional) {
        return new IValueNodeQueryConstraint(nodeLabel, entityVariableName, valueVariableName, attributePrefix, optional);
    }

    public IEntityNodeQueryConstraint nodeEntities(String nodeLabel, String entityVariableName, boolean includeSpecificEntity, boolean includeCategoryEntities, boolean optional) {
        return new IEntityNodeQueryConstraint(nodeLabel, entityVariableName, includeSpecificEntity, includeCategoryEntities, optional);
    }

    public ISiblingsQueryConstraint siblingConstraints(String nodeLabel, String entityVariableName, boolean optional) {
        return new ISiblingsQueryConstraint(nodeLabel, entityVariableName, optional);
    }

    public IBoundThroughAttributeQueryConstraint boundThroughAttribute(String entityVariableName, String nodeLabel, String valueVariableName, String typeName) {
        return new IBoundThroughAttributeQueryConstraint(entityVariableName, nodeLabel, valueVariableName, typeName, false);
    }

    public IOptionalCategoryQueryConstraint optionalCategory(String entityVariableName, String nodeLabel) {
        return new IOptionalCategoryQueryConstraint(entityVariableName, nodeLabel);
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
