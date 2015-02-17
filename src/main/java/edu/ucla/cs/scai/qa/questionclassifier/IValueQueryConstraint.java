/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cs.scai.qa.questionclassifier;

/**
 *
 * @author Giuseppe M. Mazzeo <mazzeo@cs.ucla.edu>
 */
public class IValueQueryConstraint extends IQueryConstraint {

    String valueVariableName;

    SyntacticTreeNode node;

    public IValueQueryConstraint(String valueVariableName, SyntacticTreeNode node) {
        this.valueVariableName = valueVariableName;
        this.node = node;
    }

    public String getValueVariableName() {
        return valueVariableName;
    }

    public void setValueVariableName(String valueVariableName) {
        this.valueVariableName = valueVariableName;
    }

    public SyntacticTreeNode getNode() {
        return node;
    }

    public void setNode(SyntacticTreeNode node) {
        this.node = node;
    }

}
