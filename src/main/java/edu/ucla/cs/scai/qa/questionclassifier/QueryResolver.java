/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cs.scai.qa.questionclassifier;

import edu.ucla.cs.scai.swim.qa.ontology.Ontology;
import edu.ucla.cs.scai.swim.qa.ontology.QueryConstraint;
import edu.ucla.cs.scai.swim.qa.ontology.QueryModel;
import edu.ucla.cs.scai.swim.qa.ontology.dbpedia.DBpediaOntology;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 *
 * @author Giuseppe M. Mazzeo <mazzeo@cs.ucla.edu>
 */
public class QueryResolver {

    private int entityCounter;
    private int valueCounter;

    SyntacticTree tree;

    HashMap<SyntacticTreeNode, ArrayList<QueryModel>> ppCache = new HashMap<>();
    HashMap<SyntacticTreeNode, String> ppCacheLabel = new HashMap<>();

    public QueryResolver(SyntacticTree tree) {
        this.tree = tree;
    }

    public ArrayList<QueryModel> resolveQueries(ArrayList<PennTreebankPattern> patterns) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();
        for (PennTreebankPattern pattern : patterns) {
            res.addAll(resolveIQueryModels(pattern));
        }
        return res;
    }

    private ArrayList<QueryModel> combineQueryConstraints(ArrayList<QueryModel> qms1, ArrayList<QueryModel> qms2, boolean includeQms1IfQms2isEmpty, boolean includeQms2IfQms1isEmpty) {
        ArrayList<QueryModel> res = new ArrayList<>();
        if (qms2.isEmpty()) {
            if (includeQms1IfQms2isEmpty) {
                res.addAll(qms1);
            }
        } else if (qms1.isEmpty()) {
            if (includeQms2IfQms1isEmpty) {
                res.addAll(qms2);
            }
        } else {
            for (QueryModel qm1 : qms1) {
                for (QueryModel qm2 : qms2) {
                    QueryModel qmc = new QueryModel();
                    qmc.getConstraints().addAll(qm1.getConstraints());
                    qmc.getConstraints().addAll(qm2.getConstraints());
                    res.add(qmc);
                }
            }
        }

        return res;
    }

    //this can be optimized by pre-grouping the constraints by value variable name
    private void updateAncestors(String v, HashMap<String, String> currentAncestors, Set<String> eBound, Set<String> vBound, ArrayList<QueryConstraint> constraints) {
        if (currentAncestors.containsKey(v)) {
            return;
        }

        String a = null;
        for (QueryConstraint qc : constraints) {
            if (qc.getValueExpr().equals(v)) {
                if (a != null) {
                    return; //I don't know how to cope with this case
                }
                a = qc.getSubjExpr();
            }
        }
        if (a != null) {
            if (!eBound.contains(a) && !vBound.contains(a)) {
                currentAncestors.put(v, a);
            } else {
                updateAncestors(a, currentAncestors, eBound, vBound, constraints);
                if (currentAncestors.containsKey(a)) {
                    currentAncestors.put(v, currentAncestors.get(a));
                }
            }
        }

    }

    private HashMap<String, String> computeUnboundAncestor(Set<String> eBound, Set<String> vBound, ArrayList<QueryConstraint> constraints) {
        HashMap<String, String> res = new HashMap<>();
        for (String v : eBound) {
            updateAncestors(v, res, eBound, vBound, constraints);
        }
        for (String v : vBound) {
            updateAncestors(v, res, eBound, vBound, constraints);
        }
        return res;
    }

    private void updateBoundVariables(String var, HashSet<String> boundVariables, ArrayList<QueryConstraint> constraints) {
        if (boundVariables.contains(var)) {
            return;
        }
        for (QueryConstraint qc : constraints) {
            if (qc.getSubjExpr().equals(var)) {
                if (qc.getAttrExpr().equals("isEntity") && qc.getValueExpr().startsWith("lookupEntity")
                        || qc.getAttrExpr().equals("isVal") && qc.getValueExpr().startsWith("literalVal")) {
                    boundVariables.add(var);
                    return;
                } else {
                    String var2 = qc.getValueExpr();
                    updateBoundVariables(var2, boundVariables, constraints);
                    if (boundVariables.contains(var2)) {
                        boundVariables.add(var);
                        return;
                    }
                }
            }
        }
    }

    private boolean reduceIsAttributes(QueryModel qm) {
        HashMap<String, String> isEntity = new HashMap<>();
        HashMap<String, String> isVal = new HashMap<>();

        HashSet<String> boundVariables = new HashSet<>();
        for (QueryConstraint qc : qm.getConstraints()) {
            updateBoundVariables(qc.getSubjExpr(), boundVariables, qm.getConstraints());
        }

        ArrayList<QueryConstraint> newConstraints = new ArrayList<>();

        for (Iterator<QueryConstraint> it = qm.getConstraints().iterator(); it.hasNext();) {
            QueryConstraint qc = it.next();
            if (qc.getAttrExpr().startsWith("isEntity")) {
                if (isEntity.containsKey(qc.getSubjExpr()) || isVal.containsKey(qc.getSubjExpr())) {
                    return false;
                }
                isEntity.put(qc.getSubjExpr(), qc.getValueExpr());
            } else if (qc.getAttrExpr().startsWith("isVal")) {
                if (isEntity.containsKey(qc.getSubjExpr()) || isVal.containsKey(qc.getSubjExpr())) {
                    return false;
                }
                isVal.put(qc.getSubjExpr(), qc.getValueExpr());
            } else {
                newConstraints.add(new QueryConstraint(qc.getSubjExpr(), qc.getAttrExpr(), qc.getValueExpr(), qc.isOptional()));
            }
        }

        HashMap<String, String> unboundAncestors = computeUnboundAncestor(isEntity.keySet(), isVal.keySet(), newConstraints);

        for (QueryConstraint qc : newConstraints) {
            if (boundVariables.contains(qc.getValueExpr())) { //the value is bounded
                //therefore, the subject cannot be a specific entity or value
                if (isEntity.containsKey(qc.getSubjExpr()) || isVal.containsKey(qc.getSubjExpr())) {
                    if (unboundAncestors.containsKey(qc.getSubjExpr())) {
                        qc.setSubjExpr(unboundAncestors.get(qc.getSubjExpr()));
                    } else {
                        return false;
                    }
                }
            }
            if (isEntity.containsKey(qc.getValueExpr())) {
                qc.setValueExpr(isEntity.get(qc.getValueExpr()));
            } else if (isVal.containsKey(qc.getValueExpr())) {
                qc.setValueExpr(isVal.get(qc.getValueExpr()));
            }
            if (isEntity.containsKey(qc.getSubjExpr())) {
                qc.setSubjExpr(isEntity.get(qc.getSubjExpr()));
            } else if (isVal.containsKey(qc.getSubjExpr())) {
                return false;
            }
        }
        qm.setConstraints(newConstraints);
        return true;
    }

    private ArrayList<QueryModel> resolveIQueryModel(IQueryModel iqm) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();
        for (IQueryConstraint qc : iqm.getConstraints()) {
            if (qc instanceof IEntityNodeQueryConstraint) {
                IEntityNodeQueryConstraint c = (IEntityNodeQueryConstraint) qc;
                res.addAll(resolveEntityNode(tree.labelledNodes.get(c.nodeLabel), c.entityVariableName, c.getIncludeSpecificEntity(), c.getIncludeCategorieEntities()));
            } else if (qc instanceof IValueNodeQueryConstraint) {
                IValueNodeQueryConstraint c = (IValueNodeQueryConstraint) qc;
                res.addAll(resolveValueNode(tree.labelledNodes.get(c.nodeLabel), c.entityVariableName, c.getValueVariableName(), c.getAttributePrefix()));
            } else if (qc instanceof ISiblingsQueryConstraint) {
                ISiblingsQueryConstraint c = (ISiblingsQueryConstraint) qc;
                if (!res.isEmpty()) {
                    res = combineQueryConstraints(res, resolveSiblingConstraints(tree.labelledNodes.get(c.nodeLabel), c.entityVariableName, null), true, false);
                }
            } else if (qc instanceof IBoundThroughAttributeQueryConstraint) {
                if (!res.isEmpty()) {
                    IBoundThroughAttributeQueryConstraint c = (IBoundThroughAttributeQueryConstraint) qc;
                    QueryModel qmc = new QueryModel();
                    ArrayList<QueryModel> qml = new ArrayList<>();
                    qml.add(qmc);
                    if (c.getAttributeNodes().length == 1 && !tree.labelledNodes.containsKey(c.getAttributeNodes()[0])) { //it is a reserved word - e.g. date
                        qmc.getConstraints().add(new QueryConstraint(c.entityVariableName, "defaultAttribute(" + c.getAttributeNodes()[0] + ")", c.valueVariableName, c.optional));
                    } else {
                        String attributeName = "";
                        for (String a : c.attributeNodes) {
                            if (attributeName.length() > 0) {
                                attributeName += " ";
                            }
                            attributeName += tree.labelledNodes.get(a).getLeafLemmas();
                        }
                        qmc.getConstraints().add(new QueryConstraint(c.entityVariableName, "lookupAttribute(" + attributeName + ")", c.valueVariableName, c.optional));
                    }
                    res = combineQueryConstraints(res, qml, false, false);
                }
            } else if (qc instanceof IOptionalCategoryQueryConstraint) {
                if (!res.isEmpty()) {
                    IOptionalCategoryQueryConstraint c = (IOptionalCategoryQueryConstraint) qc;
                    ArrayList<QueryModel> eqms = resolveEntityNode(tree.labelledNodes.get(c.getNodeLabel()), c.entityVariableName, false, true);
                    for (QueryModel eqm : eqms) {
                        for (QueryConstraint qc2 : eqm.getConstraints()) {
                            qc2.setOptional(true);
                        }
                    }
                    res = combineQueryConstraints(res, eqms, true, false);
                }
            }
        }
        for (Iterator<QueryModel> it = res.iterator(); it.hasNext();) {
            QueryModel qm = it.next();
            if (!reduceIsAttributes(qm)) {
                it.remove();
            }
        }
        return res;
    }

    public ArrayList<QueryModel> resolveIQueryModels(PennTreebankPattern pattern) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();
        for (IQueryModel qm : pattern.iQueryModels) {
            try {
                res.addAll(resolveIQueryModel(qm));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return res;
    }

    //resolves a NP/WHNP node, which can be either simple or compound
    private ArrayList<QueryModel> resolveEntityNode(SyntacticTreeNode node, String entityVariableName, boolean includeSpecificEntity, boolean includeCategoryEntities) throws Exception {

        ArrayList<QueryModel> res = new ArrayList<>();
        if (node.npSimple || node.whnpSimple) {

            if (includeSpecificEntity) {
                QueryModel qm = new QueryModel();
                String entityName = node.getLeafValues();
                qm.getConstraints().add(new QueryConstraint(entityVariableName, "isEntity", "lookupEntity(" + entityName + ")", false));
                res.add(qm);
            }

            if (includeCategoryEntities) {
                QueryModel qm = new QueryModel();
                String categoryName = node.getLeafLemmas();
                qm.getConstraints().add(new QueryConstraint(entityVariableName, "rdf:type", "lookupCategory(" + categoryName + ")", false));
                res.add(qm);
            }

        } else if (node.npCompound || node.whnpCompound) {
            //get the first NP child - TODO: what if the node has more NP children?
            SyntacticTreeNode np1 = null;
            for (SyntacticTreeNode c : node.children) {
                if (c.value.equals("NP")) {
                    np1 = c;
                    break;
                }
            }
            if (np1 == null) {
                return res; //node has not the structure we are looking for
            }

            ArrayList<QueryModel> qmsMainEntity = resolveEntityNode(np1, entityVariableName, includeSpecificEntity, includeCategoryEntities);

            ArrayList<QueryModel> qmsConstraints = resolveSiblingConstraints(np1, entityVariableName, "");

            res = combineQueryConstraints(qmsMainEntity, qmsConstraints, true, false);

        }
        return res;
    }

    //return the literal value represented by a node
    private ArrayList<QueryModel> resolveLiteralNode(SyntacticTreeNode node, String valueVariableName) throws Exception {

        ArrayList<QueryModel> res = new ArrayList<>();
        if (node.npSimple || node.whnpSimple) {

            QueryModel qm = new QueryModel();
            String literalValue = node.getLeafValues();
            qm.getConstraints().add(new QueryConstraint(valueVariableName, "isVal", "literalValue(" + literalValue + ")", false));
            res.add(qm);

        } else if (node.npCompound || node.whnpCompound) {

            String entityVariableName = getNextEntityVariableName();
            res = resolveValueNode(node, entityVariableName, valueVariableName, "");

        }
        return res;
    }

    //construct the query model from a NP node containing a simple NP node representing an attribute, and a PP node where the preposition is part of the attribute and the NP child represents the entity
    ArrayList<QueryModel> resolveValueNode(SyntacticTreeNode node, String entityVariableName, String valueVariableName, String attributePrefix) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();
        if (node.npCompound) {
            //get the first simple NP child - TODO: what if the node has more NP children?
            SyntacticTreeNode npAttributeNode = null;
            SyntacticTreeNode ppEntityNode = null;
            for (SyntacticTreeNode c : node.children) {
                if (c.npSimple) {
                    if (npAttributeNode != null) {
                        System.out.println("Warning: NP node with two or more simple NP children");
                    }
                    npAttributeNode = c;
                } else if (c.value.equals("PP")) {
                    if (ppEntityNode != null) {
                        System.out.println("Warning: NP node with two or more PP children");
                    }
                    ppEntityNode = c;
                }
            }
            if (npAttributeNode == null || ppEntityNode == null) {
                return res; //node has not the structure we are looking for
            }

            SyntacticTreeNode[] prepNP = extractPPprepNP(ppEntityNode);

            if (prepNP == null) {
                return res;
            }

            String attributeName = npAttributeNode.getLeafLemmas();

            ArrayList<QueryModel> qms1 = resolveEntityNode(prepNP[1], entityVariableName, true, true);
            QueryConstraint qc = new QueryConstraint(entityVariableName, "lookupAttribute(" + attributeName + "(s) [" + prepNP[0].lemma + "])", valueVariableName, false);
            for (QueryModel qm : qms1) {
                qm.getConstraints().add(qc);
                res.add(qm);
            }

            String newEntityVariable = getNextEntityVariableName();
            ArrayList<QueryModel> qms2 = resolveValueNode(prepNP[1], entityVariableName, newEntityVariable, "");
            qc = new QueryConstraint(newEntityVariable, "lookupAttribute(" + attributeName + "(s) [" + prepNP[0].lemma + "])", valueVariableName, false);
            for (QueryModel qm : qms2) {
                qm.getConstraints().add(qc);
                res.add(qm);
            }

        } else {
            //node can not be a value node
        }
        return res;
    }

    //receive a node (root) and constructs a set of contraints with the entity called entityVariableName as subject of the constraints
    //using the PP and VP siblings of the node
    ArrayList<QueryModel> resolveSiblingConstraints(SyntacticTreeNode root, String entityVariableName, String baseAttributeName) throws Exception {
        if (baseAttributeName == null) {
            baseAttributeName = "";
        } else {
            baseAttributeName = baseAttributeName.trim();
            if (baseAttributeName.length() > 0) {
                baseAttributeName += " ";
            }
        }
        ArrayList<QueryModel> res = new ArrayList<>();
        for (SyntacticTreeNode c : root.parent.children) {
            if (c == root) {
                continue;
            }

            if (c.value.equals("PP")) {
                ArrayList<QueryModel> ppConstraintsModels = resolvePPConstraint(c, entityVariableName, baseAttributeName);
                res = combineQueryConstraints(res, ppConstraintsModels, true, true);
            } else if (c.value.equals("VP")) {
                ArrayList<QueryModel> vpConstraintsModels = resolveVPConstraint(c, entityVariableName, baseAttributeName);
                res = combineQueryConstraints(res, vpConstraintsModels, true, true);
            }

        }
        return res;
    }

    //construct the query model from a NP node containing a simple NP node representing an attribute, and a PP node where the preposition is the operator and the NP child represents the literal
    private ArrayList<QueryModel> resolveLiteralConstraintNode(SyntacticTreeNode node, String entityVariableName) throws Exception {

        ArrayList<QueryModel> res = new ArrayList<>();
        if (node.npCompound || node.value.equals("VP")) {
            //get the first simple NP child - TODO: what if the node has more NP children?
            SyntacticTreeNode npAttributeNode = null;
            SyntacticTreeNode ppConstraintNode = null;
            SyntacticTreeNode vbConstraintNode = null;
            for (SyntacticTreeNode c : node.children) {
                if (c.npSimple) {
                    if (npAttributeNode != null) {
                        System.out.println("Warning: node with two or more simple NP children");
                    }
                    npAttributeNode = c;
                } else if (c.value.equals("PP")) {
                    if (ppConstraintNode != null) {
                        System.out.println("Warning: node with two or more PP children");
                    }
                    ppConstraintNode = c;
                } else if (c.value.startsWith("VB")) {
                    if (vbConstraintNode != null) {
                        System.out.println("Warning: node with two or more VB? children");
                    }
                    vbConstraintNode = c;
                }
            }
            if ((npAttributeNode == null && vbConstraintNode == null) || ppConstraintNode == null) {
                return res; //node has not the structure we are looking for
            }

            SyntacticTreeNode[] prepNP = extractPPprepNP(ppConstraintNode);

            if (prepNP == null) {
                return res;
            }

            String attributeName = npAttributeNode!=null?npAttributeNode.getLeafLemmas():vbConstraintNode.children.get(0).lemma;

            String valueVariableName1 = getNextValueVariableName();
            QueryConstraint qc1 = new QueryConstraint(entityVariableName, "lookupAttribute(" + attributeName + ")", valueVariableName1, false);

            String valueVariableName2 = getNextValueVariableName();
            QueryConstraint qc2 = new QueryConstraint(valueVariableName1, "lookupOperator(" + prepNP[0].lemma + ")", valueVariableName2, false);
            ArrayList<QueryModel> qms = resolveLiteralNode(prepNP[1], valueVariableName2);

            for (QueryModel qm : qms) {
                qm.getConstraints().add(qc1);
                qm.getConstraints().add(qc2);
                res.add(qm);
            }
        } else {
            //node can not be a value node
        }
        return res;
    }

    public ArrayList<QueryModel> resolvePPConstraint(SyntacticTreeNode node, String entityVariableName, String baseAttributeName) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();
        SyntacticTreeNode[] prepNp = extractPPprepNP(node);
        if (prepNp != null) {
            //create costraints with attributes, assuming that the values of the constraints are entities
            String newEntityName = getNextEntityVariableName();
            ArrayList<QueryModel> qmsE = resolveEntityNode(prepNp[1], newEntityName, true, true);
            for (QueryModel qm : qmsE) {
                qm.getConstraints().add(new QueryConstraint(entityVariableName, "lookupAttribute(" + baseAttributeName + prepNp[0].lemma + ")", newEntityName, false));
            }
            res.addAll(qmsE);

            String newEntityName2 = getNextEntityVariableName();
            ArrayList<QueryModel> qmsV = resolveValueNode(prepNp[1], newEntityName2, newEntityName, "");
            for (QueryModel qm : qmsV) {
                qm.getConstraints().add(new QueryConstraint(entityVariableName, "lookupAttribute(" + baseAttributeName + prepNp[0].lemma + ")", newEntityName, false));
            }
            res.addAll(qmsV);

            //create costraints with attributes, assuming that the values of the constraints are literals
            ArrayList<QueryModel> qmsL = resolveLiteralConstraintNode(prepNp[1], entityVariableName);
            res.addAll(qmsL);

        }
        return res;
    }

    public ArrayList<QueryModel> resolveVPConstraint(SyntacticTreeNode node, String entityVariableName, String baseAttribute) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();

        SyntacticTreeNode[] verbPPNP = extractVerbPPNP(node);
        if (verbPPNP != null) {
            if (verbPPNP[1] != null) {
                SyntacticTreeNode[] prepNp = extractPPprepNP(verbPPNP[1]);
                if (prepNp != null) {
                    String newEntityName = getNextEntityVariableName();
                    ArrayList<QueryModel> qmsE = resolveEntityNode(prepNp[1], newEntityName, true, true);
                    QueryConstraint qc = new QueryConstraint(entityVariableName, "lookupAttribute(" + baseAttribute + verbPPNP[0].lemma + " " + prepNp[0].lemma + ")", newEntityName, false);
                    for (QueryModel qm : qmsE) {
                        qm.getConstraints().add(qc);
                    }
                    res.addAll(qmsE);

                    String newEntityName2 = getNextEntityVariableName();
                    ArrayList<QueryModel> qmsV = resolveValueNode(prepNp[1], newEntityName2, newEntityName, "");
                    for (QueryModel qm : qmsV) {
                        qm.getConstraints().add(qc);
                    }
                    res.addAll(qmsV);

                    String newValName = getNextValueVariableName();
                    ArrayList<QueryModel> qmsL1 = resolveLiteralConstraintNode(node, entityVariableName);
                    res.addAll(qmsL1);
                }
            } else if (verbPPNP[2] != null) {
                String newEntityName = getNextEntityVariableName();
                ArrayList<QueryModel> qmsE = resolveEntityNode(verbPPNP[2], newEntityName, true, true);
                QueryConstraint qc = new QueryConstraint(entityVariableName, "lookupAttribute(" + baseAttribute + verbPPNP[0].lemma + ")", newEntityName, false);
                for (QueryModel qm : qmsE) {
                    qm.getConstraints().add(qc);
                }
                res.addAll(qmsE);

                String newEntityName2 = getNextEntityVariableName();
                ArrayList<QueryModel> qmsV = resolveValueNode(verbPPNP[2], newEntityName2, newEntityName, "");
                for (QueryModel qm : qmsV) {
                    qm.getConstraints().add(qc);
                }
                res.addAll(qmsV);

                String newValName = getNextValueVariableName();
                ArrayList<QueryModel> qmsL1 = resolveLiteralNode(verbPPNP[2], newValName);
                QueryConstraint qc1 = new QueryConstraint(entityVariableName, "lookupAttribute(" + verbPPNP[0].lemma + ")", newValName, false);
                for (QueryModel qm : qmsL1) {
                    qm.getConstraints().add(qc1);
                }
                res.addAll(qmsL1);

                ArrayList<QueryModel> qmsL2 = resolveLiteralConstraintNode(verbPPNP[2], entityVariableName);
                res.addAll(qmsL2);
            }
        }

        return res;
    }

    private String getNextEntityVariableName() {
        String res = "ent" + entityCounter;
        entityCounter++;
        return res;
    }

    private String getNextValueVariableName() {
        String res = "val" + valueCounter;
        valueCounter++;
        return res;
    }

    SyntacticTreeNode[] extractPPprepNP(SyntacticTreeNode node) {
        SyntacticTreeNode inNode = null;
        SyntacticTreeNode[] res = new SyntacticTreeNode[2];
        for (SyntacticTreeNode c : node.children) {
            if (c.value.equals("IN") || c.value.equals("TO")) {
                inNode = c;
                break;
            }
        }
        if (inNode == null) {
            System.out.println("IN/TO node not found");
            return null;
        } else {
            if (inNode.children.size() != 1) {
                System.out.println("IN/TO node with " + inNode.children.size() + " children");
                return null;
            } else {
                res[0] = inNode.children.get(0);
                SyntacticTreeNode npNode = null;
                for (SyntacticTreeNode c : node.children) {
                    if (c.value.equals("NP")) {
                        npNode = c;
                        break;
                    }
                }
                if (npNode == null) {
                    System.out.println("PP node without NP child");
                    return null;
                } else {
                    res[1] = npNode;
                }
            }
        }
        return res;
    }

    SyntacticTreeNode[] extractVerbPPNP(SyntacticTreeNode node) {
        SyntacticTreeNode vbNode = null;
        SyntacticTreeNode[] res = new SyntacticTreeNode[3];
        for (SyntacticTreeNode c : node.children) {
            if (c.value.startsWith("VB")) {
                vbNode = c;
                break;
            }
        }
        if (vbNode == null) {
            System.out.println("VB node not found");
            return null;
        } else {
            if (vbNode.children.size() != 1) {
                System.out.println("VB node with " + vbNode.children.size() + " children");
                return null;
            } else {
                res[0] = vbNode.children.get(0);

                SyntacticTreeNode ppNode = null;
                for (SyntacticTreeNode c : node.children) {
                    if (c.value.equals("PP")) {
                        ppNode = c;
                        break;
                    }
                }

                SyntacticTreeNode npNode = null;
                for (SyntacticTreeNode c : node.children) {
                    if (c.value.equals("NP")) {
                        npNode = c;
                        break;
                    }
                }

                if (ppNode == null && npNode == null) {
                    System.out.println("PP/NP node not found");
                    return null;
                } else { //TODO: what if both pp and np are not null? is it possible?
                    res[1] = ppNode;
                    res[2] = npNode;
                }

            }
        }
        return res;
    }
}
