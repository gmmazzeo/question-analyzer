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
public class IBoundThroughAttributeQueryConstraint extends IQueryConstraint {

    String entityVariableName, valueVariableName;
    String[] attributeNodes;

    public IBoundThroughAttributeQueryConstraint(String entityVariableName, String attributeNode, String valueVariableName, boolean optional) {
        super(optional);
        this.entityVariableName = entityVariableName;
        this.attributeNodes = attributeNode.replaceAll(" ", "").split("\\+");
        this.valueVariableName = valueVariableName;
    }

    public String getEntityVariableName() {
        return entityVariableName;
    }

    public void setEntityVariableName(String entityVariableName) {
        this.entityVariableName = entityVariableName;
    }

    public String getValueVariableName() {
        return valueVariableName;
    }

    public void setValueVariableName(String valueVariableName) {
        this.valueVariableName = valueVariableName;
    }

    public String[] getAttributeNodes() {
        return attributeNodes;
    }

    public void setAttributeNodes(String[] attributeNodes) {
        this.attributeNodes = attributeNodes;
    }

}
