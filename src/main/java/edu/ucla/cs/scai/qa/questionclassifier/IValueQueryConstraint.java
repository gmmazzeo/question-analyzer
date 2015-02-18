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

    String entityExpression, attributeExpression, valueExpression;
    
    public IValueQueryConstraint(String valueExpression, String valueVariableName, boolean optional) {
        super(optional);
        this.valueExpression = valueExpression;
        this.valueVariableName = valueVariableName;        
    }    

    public IValueQueryConstraint(String entityExpression, String attributeExpression, String valueVariableName, boolean optional) {
        super(optional);
        this.entityExpression = entityExpression;
        this.attributeExpression = attributeExpression;
        this.valueVariableName = valueVariableName;

    }

    public String getValueVariableName() {
        return valueVariableName;
    }

    public void setValueVariableName(String valueVariableName) {
        this.valueVariableName = valueVariableName;
    }

    public String getEntityExpression() {
        return entityExpression;
    }

    public void setEntityExpression(String entityExpression) {
        this.entityExpression = entityExpression;
    }

    public String getAttributeExpression() {
        return attributeExpression;
    }

    public void setAttributeExpression(String attributeExpression) {
        this.attributeExpression = attributeExpression;
    }

}
