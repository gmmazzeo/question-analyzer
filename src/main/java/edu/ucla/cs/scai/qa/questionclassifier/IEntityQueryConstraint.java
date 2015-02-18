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
public class IEntityQueryConstraint extends IQueryConstraint {

    String entityVariableName;

    String entityExpression, attributeExpression, valueExpression;

    public IEntityQueryConstraint(String entityExpression, String entityVariableName, boolean optional) {
        super(optional);
        this.entityExpression = entityExpression;
        this.entityVariableName = entityVariableName;
    }

    public IEntityQueryConstraint(String entityVariableName, String attributeExpression, String valueExpression, boolean optional) {
        super(optional);
        this.entityVariableName = entityVariableName;
        this.attributeExpression = attributeExpression;
        this.valueExpression = valueExpression;

    }

    public String getEntityVariableName() {
        return entityVariableName;
    }

    public void setEntityVariableName(String entityVariableName) {
        this.entityVariableName = entityVariableName;
    }
}
