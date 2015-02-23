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
public class ISiblingsQueryConstraint extends IQueryConstraint {

    String entityVariableName;

    String nodeLabel;

    public ISiblingsQueryConstraint(String nodeLabel, String entityVariableName, boolean optional) {
        super(optional);
        this.nodeLabel = nodeLabel;
        this.entityVariableName = entityVariableName;
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
}
