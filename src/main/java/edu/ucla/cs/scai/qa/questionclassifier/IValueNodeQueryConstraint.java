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
public class IValueNodeQueryConstraint extends IQueryConstraint {

    String entityVariableName;
    String valueVariableName;
    String nodeLabel;
    String attributePrefix;

    public IValueNodeQueryConstraint(String nodeLabel, String entityVariableName, String valueVariableName, String attributePrefix, boolean optional) {
        super(optional);
        this.nodeLabel = nodeLabel;
        this.entityVariableName = entityVariableName;
        this.valueVariableName = valueVariableName;
        this.attributePrefix = attributePrefix;
    }

    public String getEntityVariableName() {
        return entityVariableName;
    }

    public void setEntityVariableName(String entityVariableName) {
        this.entityVariableName = entityVariableName;
    }

    public String getNodeLabel() {
        return nodeLabel;
    }

    public void setNodeLabel(String nodeLabel) {
        this.nodeLabel = nodeLabel;
    }

    public String getValueVariableName() {
        return valueVariableName;
    }

    public void setValueVariableName(String valueVariableName) {
        this.valueVariableName = valueVariableName;
    }

    public String getAttributePrefix() {
        return attributePrefix;
    }

    public void setAttributePrefix(String attributePrefix) {
        this.attributePrefix = attributePrefix;
    }

}
