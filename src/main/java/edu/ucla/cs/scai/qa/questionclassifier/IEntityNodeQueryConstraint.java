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
public class IEntityNodeQueryConstraint extends IQueryConstraint {

    String entityVariableName;

    String nodeLabel;

    boolean includeSpecificEntity;

    boolean includeCategoryEntities;

    public IEntityNodeQueryConstraint(String nodeLabel, String entityVariableName, boolean includeSpecificEntity, boolean includeCategoryEntities, boolean optional) {
        super(optional);
        this.nodeLabel = nodeLabel;
        this.entityVariableName = entityVariableName;
        this.includeSpecificEntity = includeSpecificEntity;
        this.includeCategoryEntities = includeCategoryEntities;
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

    public boolean getIncludeSpecificEntity() {
        return includeSpecificEntity;
    }

    public void setIncludeSpecificEntity(boolean includeSpecificEntity) {
        this.includeSpecificEntity = includeSpecificEntity;
    }

    public boolean getIncludeCategorieEntities() {
        return includeCategoryEntities;
    }

    public void setIncludeCategorieEntities(boolean includeCategorieEntities) {
        this.includeCategoryEntities = includeCategorieEntities;
    }

}
