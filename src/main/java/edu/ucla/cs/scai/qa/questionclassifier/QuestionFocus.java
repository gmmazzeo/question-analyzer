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
public class QuestionFocus {

    SyntacticTreeNode node;
    int categoryPriority;
    int attributePriority;
    int entityPriority;

    public QuestionFocus(SyntacticTreeNode node, int categoryPriority, int attributePriority, int entityPriority) {
        this.node = node;
        this.categoryPriority = categoryPriority;
        this.attributePriority = attributePriority;
        this.entityPriority = entityPriority;
    }

    public SyntacticTreeNode getNode() {
        return node;
    }

    public void setNode(SyntacticTreeNode node) {
        this.node = node;
    }

    public int getCategoryPriority() {
        return categoryPriority;
    }

    public void setCategoryPriority(int categoryPriority) {
        this.categoryPriority = categoryPriority;
    }

    public int getAttributePriority() {
        return attributePriority;
    }

    public void setAttributePriority(int attributePriority) {
        this.attributePriority = attributePriority;
    }

    public int getEntityPriority() {
        return entityPriority;
    }

    public void setEntityPriority(int entityPriority) {
        this.entityPriority = entityPriority;
    }

}
