/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cs.scai.qa.questionclassifier;

import edu.ucla.cs.scai.swim.qa.ontology.NamedEntityAnnotationResult;
import edu.ucla.cs.scai.swim.qa.ontology.QueryConstraint;
import edu.ucla.cs.scai.swim.qa.ontology.QueryModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Giuseppe M. Mazzeo <mazzeo@cs.ucla.edu>
 */
public class QueryResolver2 {

    private int entityCounter;
    private int valueCounter;

    SyntacticTree tree;

    HashMap<SyntacticTreeNode, ArrayList<QueryModel>> ppCache = new HashMap<>();
    HashMap<SyntacticTreeNode, String> ppCacheLabel = new HashMap<>();

    public QueryResolver2(SyntacticTree tree) {
        this.tree = tree;
    }

    public ArrayList<QueryModel> resolveQueries(ArrayList<PennTreebankPattern> patterns) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();
        for (PennTreebankPattern pattern : patterns) {
            res.addAll(resolveIQueryModels(pattern));
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

    private ArrayList<QueryModel> resolveIQueryModel(IQueryModel iqm) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();
        for (IQueryConstraint qc : iqm.getConstraints()) {
            if (qc instanceof IEntityNodeQueryConstraint) {
                IEntityNodeQueryConstraint c = (IEntityNodeQueryConstraint) qc;
                ArrayList<QueryModel> entity = resolveEntityNode(tree.labelledNodes.get(c.nodeLabel), c.entityVariableName, c.includeSpecificEntity, c.includeCategoryEntities, new ArrayList<SyntacticTreeNode>());
                if (entity.isEmpty()) {
                    return new ArrayList<>();
                }
                res = combineQueryConstraints(res, entity, true, true);
            } else if (qc instanceof IValueNodeQueryConstraint) {
                IValueNodeQueryConstraint c = (IValueNodeQueryConstraint) qc;
                ArrayList<QueryModel> values = resolveValueNode(tree.labelledNodes.get(c.nodeLabel), c.entityVariableName, c.valueVariableName, /*c.attributePrefix*/ new ArrayList<SyntacticTreeNode>());
                if (values.isEmpty()) {
                    return new ArrayList<>();
                }
                res = combineQueryConstraints(res, values, true, true);
            } else if (qc instanceof ISiblingsQueryConstraint && !res.isEmpty()) {
                ISiblingsQueryConstraint c = (ISiblingsQueryConstraint) qc;
                res = combineQueryConstraints(res, resolveSiblingConstraints(tree.labelledNodes.get(c.nodeLabel), c.entityVariableName, new ArrayList<SyntacticTreeNode>(), c.includeSelf), true, c.independent);
            } else if (qc instanceof IBoundThroughAttributeQueryConstraint) {
                IBoundThroughAttributeQueryConstraint c = (IBoundThroughAttributeQueryConstraint) qc;
                res = combineQueryConstraints(res, resolveBoundThroughAttributeConstraint(c), true, true);
            } else if (qc instanceof IOptionalCategoryQueryConstraint && !res.isEmpty()) {
                IOptionalCategoryQueryConstraint c = (IOptionalCategoryQueryConstraint) qc;
                res = combineQueryConstraints(res, resolveOptionalCategoryConstraint(c), true, false);
            }
        }
        System.out.println("Initial query models: " + res.size());
        for (Iterator<QueryModel> it = res.iterator(); it.hasNext();) {
            QueryModel qm = it.next();
            if (!reduceIsAttributes(qm) || qm.getConstraints().isEmpty() && qm.getExampleEntity() == null) {
                it.remove();
                //System.out.println("Dropped:\n" + qm);
            } else {
                for (Iterator<QueryConstraint> it2 = qm.getConstraints().iterator(); it2.hasNext();) {
                    QueryConstraint qc = it2.next();
                    if (qc.getAttrExpr().startsWith("lookupOperator")) {
                        it2.remove();
                        qm.getFilters().add(qc);
                    } else if (qc.getAttrExpr().equals("isLiteral")) {
                        it2.remove();
                        qc.setAttrExpr("lookupOperator(equalsLiteral)");
                        qm.getFilters().add(qc);
                    }
                }
            }
        }
        System.out.println("Final query models: " + res.size());
        return res;
    }

    private ArrayList<QueryModel> combineQueryConstraints(ArrayList<QueryModel> qms1, ArrayList<QueryModel> qms2, boolean includeQms1IfQms2isEmpty, boolean includeQms2IfQms1isEmpty) {
        ArrayList<QueryModel> res = new ArrayList<>();

        if (qms2.isEmpty() && includeQms1IfQms2isEmpty) {
            res.addAll(qms1);
        } else if (qms1.isEmpty() && includeQms2IfQms1isEmpty) {
            res.addAll(qms2);
        } else {
            for (QueryModel qm1 : qms1) {
                for (QueryModel qm2 : qms2) {
                    QueryModel qmc = new QueryModel(qm1.getEntityVariableName(), qm1.getAttributeVariableName());
                    qmc.setWeight(qm1.getWeight() * qm2.getWeight());
                    qmc.getConstraints().addAll(qm1.getConstraints());
                    qmc.getConstraints().addAll(qm2.getConstraints());
                    for (Map.Entry<String, HashSet<String>> e : qm1.getIgnoreEntitiesForLookup().entrySet()) {
                        qmc.getIgnoreEntitiesForLookup().put(e.getKey(), e.getValue());
                    }
                    for (Map.Entry<String, HashSet<String>> e : qm2.getIgnoreEntitiesForLookup().entrySet()) {
                        HashSet<String> current = qmc.getIgnoreEntitiesForLookup().get(e.getKey());
                        if (current == null) {
                            qmc.getIgnoreEntitiesForLookup().put(e.getKey(), e.getValue());
                        } else {
                            current.addAll(e.getValue());
                        }
                    }
                    res.add(qmc);
                }
            }
        }

        return res;
    }

    private SyntacticTreeNode[] npExtension(SyntacticTreeNode node) {
        if (node.npCompound) {
            //get the first simple NP child - TODO: what if the node has more NP children?
            SyntacticTreeNode npNode = null;
            SyntacticTreeNode ppNode = null;
            for (SyntacticTreeNode c : node.children) {
                if (c.npSimple) {
                    if (npNode == null) {
                        npNode = c;
                    }
                } else if (c.value.equals("PP")) {
                    if (ppNode == null) {
                        ppNode = c;
                    }
                }
            }
            if (npNode == null || ppNode == null) {
                return null; //node has not the structure we are looking for
            }

            SyntacticTreeNode[] inNpAdvp = extractInNpAdvpFromPp(ppNode);

            if (!(inNpAdvp[0].children.get(0).lemma.equals("of") || inNpAdvp[0].children.get(0).lemma.equals("in")|| inNpAdvp[0].children.get(0).lemma.equals("on")) || inNpAdvp[2] != null) {
                return null; //this was added to limit the possibile combinations - in which cases nouns linked through a preposition different from "of" can represent attribute, entities or category names?
            }

            return inNpAdvp;

        } else {
            return null;
        }
    }

    private boolean overlap(SyntacticTreeNode node, NamedEntityAnnotationResult annotation) {
        int inf = Math.max(node.begin, annotation.getBegin());
        int sup = Math.min(node.end, annotation.getEnd());
        return sup > inf;
    }

    private boolean overlap(Collection<SyntacticTreeNode> nodes, NamedEntityAnnotationResult annotation) {
        for (SyntacticTreeNode node : nodes) {
            if (overlap(node, annotation)) {
                return true;
            }
        }
        return false;
    }

    //a list of nodes matches with an annotation if
    //1) the list contains all the tokens of the annotation
    //2) the list does not contain any other "relevant" node
    //it is assumed that the list of nodes is sorted according to their order in the sentence
    private ArrayList<NamedEntityAnnotationResult> getMatchingAnnotations(ArrayList<SyntacticTreeNode> nodes) {
        ArrayList<NamedEntityAnnotationResult> res = new ArrayList<>();
        int inf = nodes.get(0).begin;
        int sup = nodes.get(nodes.size() - 1).end;
        for (NamedEntityAnnotationResult ar : tree.namedEntityAnnotations) {
            if (ar.getBegin() < inf || ar.getEnd() > sup) { //condition 1 is non satisfied
                continue;
            }
            boolean validMatching = true;
            for (SyntacticTreeNode n : nodes) {
                if (!overlap(n, ar) && !n.value.equals("DT") && !n.value.equals("WDT") && !n.value.equals("POS") && !n.value.equals("JJ")) { //condition 2 is not satisfied
                    validMatching = false;
                    break;
                }
            }
            if (validMatching) {
                res.add(ar);
            }
        }
        return res;
    }

    //returns true if the nodes partially overlap with at least one annotation
    private boolean partialAnnotationsOverlap(ArrayList<SyntacticTreeNode> nodes) {
        ArrayList<NamedEntityAnnotationResult> res = new ArrayList<>();
        int inf = nodes.get(0).begin;
        int sup = nodes.get(nodes.size() - 1).end;
        for (NamedEntityAnnotationResult ar : tree.namedEntityAnnotations) {
            if (ar.getEnd() < inf || ar.getBegin() > sup) { //there is no overlap
                continue;
            }
            //there is overlap
            if (ar.getBegin() < inf || ar.getEnd() > sup) { //some token of the annotation is outside the nodes
                return true;
            }
            //the annotation is completely contained in the nodes
            for (SyntacticTreeNode n : nodes) {
                if (!overlap(n, ar) && !n.value.equals("DT") && !n.value.equals("WDT") && !n.value.equals("POS")) { //a relevant node is not contained in the annotation
                    return true;
                }
            }
        }
        return false;
    }

    private String lookupEntity(ArrayList<SyntacticTreeNode> nodes) throws Exception {
        String res = "lookupEntity(";
        boolean first = true;
        for (SyntacticTreeNode n : nodes) {
            String newValues = n.getLeafValues();
            if (newValues.length() > 0) {
                if (first) {
                    first = false;
                } else {
                    res += " ";
                }
                res += newValues;
            }
        }
        res += ")";
        if (res.length() == 14) { //the argument of the lookupEntity is missing
            throw new Exception("No leaf nodes can be used for looking up an entity");
        }
        return res;
    }

    private String lookupLiteral(ArrayList<SyntacticTreeNode> nodes) throws Exception {
        String res = "lookupLiteral(";
        boolean first = true;
        for (SyntacticTreeNode n : nodes) {
            String newValues = n.getLeafValues();
            if (newValues.length() > 0) {
                if (first) {
                    first = false;
                } else {
                    res += " ";
                }
                res += newValues;
            }
        }
        res += ")";
        if (res.length() == 15) { //the argument of the lookupLiteral is missing
            throw new Exception("No leaf nodes can be used for looking up a literal");
        }
        return res;
    }

    private String lookupCategory(ArrayList<SyntacticTreeNode> nodes) throws Exception {
        String res = "lookupCategory(";
        boolean first = true;
        for (SyntacticTreeNode n : nodes) {
            String newValues = n.getLeafLemmas();
            if (newValues.length() > 0) {
                if (first) {
                    first = false;
                } else {
                    res += " ";
                }
                res += newValues;
            }
        }
        res += ")";
        if (res.length() == 16) { //the argument of the lookupCategory is missing
            throw new Exception("No leaf nodes can be used for looking up a category");
        }
        return res;
    }

    private String lookupAttribute(ArrayList<SyntacticTreeNode> nodes) throws Exception {
        String res = "lookupAttribute(";
        boolean first = true;
        for (SyntacticTreeNode n : nodes) {
            String newValues = n.getLeafValues();
            if (newValues.length() > 0) {
                if (first) {
                    first = false;
                } else {
                    res += " ";
                }
                res += newValues;
            }
        }
        res += ")";
        if (res.length() == 17) { //the argument of the lookupAttribute is missing
            throw new Exception("No leaf nodes can be used for looking up an attribute");
        }
        return res;
    }

    private String lookupAttribute(ArrayList<SyntacticTreeNode> nodes, String typeName) {
        String res = "lookupAttribute(";
        boolean first = true;
        for (SyntacticTreeNode n : nodes) {
            String newValues = n.getLeafValues();
            if (newValues.length() > 0) {
                if (first) {
                    first = false;
                } else {
                    res += " ";
                }
                res += newValues;
            }
        }
        if (!typeName.isEmpty()) {
            res += " " + typeName;
        }
        res += ")";
        return res;
    }

    private String lookupOperator(ArrayList<SyntacticTreeNode> nodes) {
        String res = "lookupOperator(";
        boolean first = true;
        for (SyntacticTreeNode n : nodes) {
            String newValues = n.getLeafValues();
            if (newValues.length() > 0) {
                if (first) {
                    first = false;
                } else {
                    res += " ";
                }
                res += newValues;
            }
        }
        res += ")";
        return res;
    }

    //resolves a NP/WHNP node, which can be either simple or compound
    private ArrayList<QueryModel> resolveEntityNode(SyntacticTreeNode node, String entityVariableName, boolean includeSpecificEntity, boolean includeCategoryEntities, ArrayList<SyntacticTreeNode> prefix) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();
        for (SyntacticTreeNode c : node.children) { //nodes containing numbers can't be entities - why?
            if (c.value.equals("CD")) {
                return res;
            }
        }

        if (node.saxonGenitiveParent && includeCategoryEntities) { //"e1's e2" if not meaningful if e2 is a specific entity - it would be a specific entity with a constraint (it would be dropped later)
            //TODO: handle prefix
            SyntacticTreeNode np1 = null;
            ArrayList<SyntacticTreeNode> categoryNodes = new ArrayList<>();
            for (SyntacticTreeNode c : node.children) {
                if (c.saxonGenitive) {
                    np1 = c;
                } else {
                    categoryNodes.add(c);
                }
            }
            if (np1 == null || categoryNodes.isEmpty()) {
                return res; //node has not the structure we are looking for
            }

            String possessorVarName = getNextEntityVariableName();
            ArrayList<QueryModel> qmsPossessor = resolveEntityNode(np1, possessorVarName, true, true, new ArrayList<SyntacticTreeNode>());
            QueryConstraint qc1 = new QueryConstraint(entityVariableName, "rdf:type", lookupCategory(categoryNodes), false);
            QueryConstraint qc2 = new QueryConstraint(entityVariableName, "lookupAttribute(of)", possessorVarName, false);
            for (QueryModel qm1 : qmsPossessor) {
                qm1.getConstraints().add(qc1);
                qm1.getConstraints().add(qc2);
                qm1.setEntityVariableName(entityVariableName);
            }
            res.addAll(qmsPossessor);
        } else if (node.npSimple || node.whnpSimple) {
            ArrayList<SyntacticTreeNode> entityNodes = new ArrayList<>(prefix);
            entityNodes.addAll(node.getLeafParents());
            boolean plural = false;
            for (SyntacticTreeNode n : entityNodes) {
                if (n.value.startsWith("N")) {
                    plural = n.value.endsWith("S");
                }
            }
            double maxWeight = 0;
            double minWeight = 1;
            boolean annotationsFound = false;
            boolean partialAnnotationOverlap = partialAnnotationsOverlap(entityNodes);
            if (includeSpecificEntity) {
                ArrayList<NamedEntityAnnotationResult> annotations = getMatchingAnnotations(entityNodes);
                annotationsFound = !annotations.isEmpty();
                for (NamedEntityAnnotationResult ar : annotations) {
                    QueryModel qm = new QueryModel(entityVariableName, null);
                    qm.setWeight(ar.getWeight());
                    //System.out.println(ar.getNamedEntity().getName() + " " + ar.getWeight());
                    maxWeight = Math.max(maxWeight, ar.getWeight());
                    minWeight = Math.min(minWeight, ar.getWeight());
                    QueryConstraint qc = new QueryConstraint(entityVariableName, "isEntity", ar.getNamedEntity().getUri(), false);
                    qc.setValueEntity(ar.getNamedEntity());
                    qm.getConstraints().add(qc);
                    res.add(qm);
                }
                //minWeight = (minWeight == maxWeight) ? 0 : minWeight;

                QueryModel qm1 = new QueryModel(entityVariableName, null);
                QueryConstraint qc = new QueryConstraint(entityVariableName, "isEntity", lookupEntity(entityNodes), false);
                qm1.getConstraints().add(qc);
                if (annotationsFound) {
                    qm1.setWeight(0.8 * maxWeight);
                    HashSet<String> entitiesToIgnore = new HashSet<>();
                    qm1.getIgnoreEntitiesForLookup().put(qc.getValueExpr().substring(13, qc.getValueExpr().length() - 1), entitiesToIgnore);
                    for (NamedEntityAnnotationResult ar : annotations) {
                        entitiesToIgnore.add(ar.getNamedEntity().getUri());
                    }
                } else {
                    if (plural) {
                        qm1.setWeight(0.5 * (partialAnnotationOverlap ? 0.8 : 1));
                    } else {
                        qm1.setWeight(1 * (partialAnnotationOverlap ? 0.8 : 1));
                    }
                }
                res.add(qm1);

                QueryModel qm2 = new QueryModel(entityVariableName, null);
                qm2.setWeight(0.8);
                qm2.getConstraints().add(new QueryConstraint(entityVariableName, "isLiteral", lookupLiteral(entityNodes), false));
                if (annotationsFound) {
                    qm2.setWeight(0.5 * minWeight);
                } else if (partialAnnotationOverlap) {
                    qm2.setWeight(0.2);
                }
                res.add(qm2);
            }
            if (includeCategoryEntities) {
                QueryModel qm = new QueryModel(entityVariableName, null);
                qm.getConstraints().add(new QueryConstraint(entityVariableName, "rdf:type", lookupCategory(entityNodes), false));
                if (annotationsFound) {
                    //TODO: max weight should be used - if max weight is low, it is more likely that this node represent a category
                    //the idea is: the node is either an entity or a category (it cannot be both)
                    //so, the sum of the weights should for the different models be constants
                    //even the weights for literals should be involded in this reasoning
                    qm.setWeight(1 - (maxWeight + minWeight) * 0.5);
                } else {
                    qm.setWeight(partialAnnotationOverlap ? 0.2 : 0.8);
                }
                res.add(qm);
            }
        } else if ((node.npCompound || node.whnpCompound || node.value.equals("WHPP")) && !node.saxonGenitiveParent) {
            //get the first NP child - TODO: what if the node has more NP children?
            SyntacticTreeNode np = null;
            for (SyntacticTreeNode c : node.children) {
                if (c.value.equals("NP") || c.value.equals("WHNP")) {
                    np = c;
                    break;
                }
            }
            if (np == null) {
                return res; //node has not the structure we are looking for
            }

            ArrayList<QueryModel> qmsMainEntity = resolveEntityNode(np, entityVariableName, includeSpecificEntity, includeCategoryEntities, prefix);
            ArrayList<QueryModel> qmsConstraints = resolveSiblingConstraints(np, entityVariableName, new ArrayList<SyntacticTreeNode>(), false);
            res = combineQueryConstraints(qmsMainEntity, qmsConstraints, true, false);

            SyntacticTreeNode[] npExt = npExtension(node);
            if (npExt != null) {
                ArrayList<SyntacticTreeNode> newPrefix = new ArrayList<>(prefix);
                newPrefix.addAll(np.getLeafParents());
                newPrefix.addAll(npExt[0].getLeafParents());
                ArrayList<QueryModel> qm2 = resolveEntityNode(npExt[1], entityVariableName, includeSpecificEntity, includeCategoryEntities, newPrefix);
                res.addAll(qm2);
            }
        }
        return res;
    }

    //return the literal value represented by a node
    private ArrayList<QueryModel> resolveLiteralNode(SyntacticTreeNode node, String valueVariableName) throws Exception {
        //TODO: handle saxon genitive nodes (eg., John's wife) - May be nothing has to be done
        ArrayList<QueryModel> res = new ArrayList<>();
        if (node.npSimple || node.whnpSimple) {
            QueryModel qm = new QueryModel(valueVariableName, null);
            String literalValue = node.getLeafValues();
            qm.getConstraints().add(new QueryConstraint(valueVariableName, "isLiteral", "literalValue(" + literalValue + ")", false));
            for (NamedEntityAnnotationResult ar : tree.namedEntityAnnotations) {
                if (overlap(node, ar)) {
                    qm.setWeight(0.2);
                    break;
                }
            }
            res.add(qm);
        } else if (node.npCompound || node.whnpCompound) { ////This needed for case such as "...greater than the population of California")
            String entityVariableName = getNextEntityVariableName();
            res = resolveValueNode(node, entityVariableName, valueVariableName, new ArrayList<SyntacticTreeNode>());
        }
        return res;
    }

    //construct the query model from a NP node containing a simple NP node representing an attribute, and a PP node where the preposition is part of the attribute and the NP child represents the entity
    ArrayList<QueryModel> resolveValueNode(SyntacticTreeNode node, String entityVariableName, String valueVariableName, ArrayList<SyntacticTreeNode> attributePrefix) throws Exception {
        //TODO: handle saxon genitive nodes (eg., John's wife) - May be nothing has to be done
        ArrayList<QueryModel> res = new ArrayList<>();

        if (node.saxonGenitiveParent) {
            SyntacticTreeNode np1 = null;
            ArrayList<SyntacticTreeNode> attributeNodes = new ArrayList<>(attributePrefix); //TODO: find an example where the attributePrexix is not empty
            for (SyntacticTreeNode c : node.children) {
                if (c.saxonGenitive) {
                    np1 = c;
                } else {
                    attributeNodes.add(c);
                }
            }
            if (np1 == null || attributeNodes.isEmpty()) {
                return res; //node has not the structure we are looking for
            }

            ArrayList<QueryModel> qmsMainEntity = resolveEntityNode(np1, entityVariableName, true, true, new ArrayList<SyntacticTreeNode>());
            QueryConstraint qc = new QueryConstraint(entityVariableName, lookupAttribute(attributeNodes), valueVariableName, false);
            for (QueryModel qm1 : qmsMainEntity) {
                qm1.getConstraints().add(qc);
                qm1.setAttributeVariableName(valueVariableName);
            }
            res.addAll(qmsMainEntity);
        } else if (node.npCompound || node.whnpCompound) {
            //get the first simple NP child - TODO: what if the node has more NP children?
            SyntacticTreeNode npAttributeNode = null;
            SyntacticTreeNode ppEntityNode = null;
            for (SyntacticTreeNode c : node.children) {
                if (c.npSimple || c.whnpSimple) {
                    if (npAttributeNode != null) {
                        System.out.println("Warning: NP node with two or more simple NP children");
                    }
                    npAttributeNode = c;
                } else if (c.value.equals("PP") || c.value.equals("WHPP")) {
                    if (ppEntityNode != null) {
                        System.out.println("Warning: NP node with two or more PP children");
                    }
                    ppEntityNode = c;
                }
            }
            if (npAttributeNode == null || ppEntityNode == null) {
                return res; //node has not the structure we are looking for
            }

            SyntacticTreeNode[] inNpAdvp = extractInNpAdvpFromPp(ppEntityNode);

            if (inNpAdvp == null || inNpAdvp[0].getLeafValues().equals("with")) {
                return res;
            }

            //what if advp (inNpAdvp[2]) is not null?
            ArrayList<SyntacticTreeNode> attributeNodes = new ArrayList<>(attributePrefix);
            ArrayList<SyntacticTreeNode> attributeName = npAttributeNode.getLeafParents();
            attributeNodes.addAll(attributeName);

            boolean plural = false;
            for (SyntacticTreeNode n : attributeName) {
                if (n.value.startsWith("N")) {
                    plural = n.value.endsWith("S");
                }
            }

            String lookupAttribute = lookupAttribute(attributeNodes);
            ArrayList<QueryModel> qms1 = resolveEntityNode(inNpAdvp[1], entityVariableName, true, true, new ArrayList<SyntacticTreeNode>());
            for (QueryModel qm : qms1) {
                qm.setAttributeVariableName(valueVariableName);
                qm.getConstraints().add(new QueryConstraint(entityVariableName, lookupAttribute, valueVariableName, false));
                if (plural) {
                    qm.setWeight(qm.getWeight() * 0.9);
                }
                if (attributePrefix.toString().contains("of") && npAttributeNode.toString().matches("the [a-z]")) {
                    qm.setWeight(qm.getWeight() * 0.8);
                }
            }
            res.addAll(qms1);

            String newEntityVariable = getNextEntityVariableName();
            ArrayList<QueryModel> qms2 = resolveValueNode(inNpAdvp[1], entityVariableName, newEntityVariable, new ArrayList<SyntacticTreeNode>());
            for (QueryModel qm : qms2) {
                qm.setAttributeVariableName(valueVariableName);
                qm.getConstraints().add(new QueryConstraint(newEntityVariable, lookupAttribute, valueVariableName, false));
                if (plural) {
                    qm.setWeight(qm.getWeight() * 0.9);
                }
            }
            res.addAll(qms2);

            SyntacticTreeNode[] npExt = npExtension(node);
            if (npExt != null) {
                attributeNodes.addAll(npExt[0].getLeafParents());
                ArrayList<QueryModel> qms3 = resolveValueNode(npExt[1], entityVariableName, valueVariableName, attributeNodes);
                for (QueryModel qm : qms3) {if (plural) {
                        qm.setWeight(qm.getWeight() * 0.9);
                    }
                }
                res.addAll(qms3);
            }

        } else {
            //node can not be a value node
        }
        return res;
    }

    //receive a node and constructs a set of constraints with the entity called entityVariableName as subject of the constraints
    //using the PP and VP siblings of the node
    ArrayList<QueryModel> resolveSiblingConstraints(SyntacticTreeNode node, String entityVariableName, ArrayList<SyntacticTreeNode> baseAttribute, boolean includeSelf) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();
        boolean constraintFound = false;
        for (SyntacticTreeNode c : node.parent.children) {
            if (c == node && !includeSelf) {
                continue;
            }
            if (c.value.equals("PP")) {
                constraintFound = true;
                res = combineQueryConstraints(res, resolvePPConstraint(c, entityVariableName, baseAttribute), true, true);
            } else if (c.value.equals("VP")) {
                constraintFound = true;
                res = combineQueryConstraints(res, resolveVPConstraint(c, entityVariableName, baseAttribute), true, true);
            } else if (c.value.equals("SBAR")) {
                constraintFound = true;
                SyntacticTreeNode vp = extractVPfromSBAR(c);
                if (vp != null) {
                    res = combineQueryConstraints(res, resolveVPConstraint(vp, entityVariableName, baseAttribute), true, true);
                }
            }
        }
        if (res.isEmpty() && constraintFound) {
            throw new Exception("constraintResolution failed");
        }
        return res;
    }

    private ArrayList<QueryModel> resolveBoundThroughAttributeConstraint(IBoundThroughAttributeQueryConstraint c) {
        ArrayList<QueryModel> res = new ArrayList<>();
        QueryModel qm = new QueryModel(c.getEntityVariableName(), c.getValueVariableName());
        res.add(qm);
        if (c.getAttributeNodes().length == 1 && !tree.labelledNodes.containsKey(c.getAttributeNodes()[0])) { //it is a reserved word - e.g. date
            qm.getConstraints().add(new QueryConstraint(c.entityVariableName, "lookupAttribute(" + c.getAttributeNodes()[0].split("#")[1] + ")", c.valueVariableName, c.optional));
        } else {
            ArrayList<SyntacticTreeNode> attributeName = new ArrayList<>();
            for (String a : c.attributeNodes) {
                attributeName.addAll(tree.labelledNodes.get(a).getLeafParents());
            }

            //TODO: explain this!
            SyntacticTreeNode ppEntityNode = null;
            for (String a : c.attributeNodes) {
                if (tree.labelledNodes.get(a).value.equals("PP")) {
                    if (ppEntityNode != null) {
                        System.out.println("Warning: node with two or more PP children");
                    }
                    ppEntityNode = tree.labelledNodes.get(a);
                }
            }

            if (ppEntityNode != null) {
                SyntacticTreeNode[] prepNP = extractInNpAdvpFromPp(ppEntityNode);

                if (prepNP != null) {
                    attributeName.addAll(prepNP[0].getLeafParents());
                }
            }

            //qm.getConstraints().add(new QueryConstraint(c.entityVariableName, "lookupAttribute(" + attributeName + prep + (c.typeName.isEmpty() ? "" : " " + c.typeName) + ")", c.valueVariableName, c.optional));
            //TODO: remove the typeName from the lookupAttribute argument and use it later during the resolution of the attribute (QueryMapping) of the contraint - better not to mix semantic elements with strings in strings, since it can be done later, when necesessary
            qm.getConstraints().add(new QueryConstraint(c.entityVariableName, lookupAttribute(attributeName, c.typeName), c.valueVariableName, c.typeName, c.optional));
        }
        return res;
    }

    private ArrayList<QueryModel> resolveOptionalCategoryConstraint(IOptionalCategoryQueryConstraint c) throws Exception {
        if (!tree.labelledNodes.containsKey(c.getNodeLabel())) { //it is a reserved word - e.g. person
            ArrayList<QueryModel> res = new ArrayList<>();
            QueryModel qm = new QueryModel("", "");
            qm.getConstraints().add(new QueryConstraint(c.entityVariableName, "rdf:type", "lookupCategory(" + c.getNodeLabel().split("#")[1] + ")", false));
            res.add(qm);
            return res;
        }

        ArrayList<QueryModel> res = resolveEntityNode(tree.labelledNodes.get(c.getNodeLabel()), c.entityVariableName, false, true, new ArrayList<SyntacticTreeNode>());
        for (QueryModel qm : res) {
            for (QueryConstraint qc : qm.getConstraints()) {
                qc.setOptional(true);
            }
        }
        return res;
    }

    //construct the query model from a NP node containing a simple NP node representing an attribute, and a PP node where the preposition is the operator and the NP child represents the literal
    private ArrayList<QueryModel> resolveLiteralConstraint(SyntacticTreeNode node, String entityVariableName, ArrayList<SyntacticTreeNode> attributePrefix) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();
        if (node.npCompound || node.value.equals("VP")) {
            //get the first simple NP child - TODO: what if the node has more NP children?
            if (node.value.equals("VP")) {
                System.out.print("");
            }
            SyntacticTreeNode npAttributeNode = null;
            SyntacticTreeNode ppConstraintNode = null;
            SyntacticTreeNode vbConstraintNode = null;
            SyntacticTreeNode adjpConstraintNode = null;
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
                } else if (c.value.equals("ADJP")) {
                    if (adjpConstraintNode != null) {
                        System.out.println("Warning: node with two or more ADJP children");
                    }
                    adjpConstraintNode = c;
                }
            }

            SyntacticTreeNode adjOp = null;
            if (ppConstraintNode == null && adjpConstraintNode != null) { //then adjpConstraint is not null
                for (SyntacticTreeNode c : adjpConstraintNode.children) {
                    if (c.value.equals("JJ")) {
                        adjOp = c;
                    } else if (c.value.equals("PP")) {
                        ppConstraintNode = c;
                    }
                }
            }

            if ((npAttributeNode == null && vbConstraintNode == null) || ppConstraintNode == null) {
                return res; //node has not the structure we are looking for
            }

            SyntacticTreeNode[] prepNP = extractInNpAdvpFromPp(ppConstraintNode);

            if (prepNP == null) {
                return res;
            }

            ArrayList<SyntacticTreeNode> attributeName = new ArrayList<>(attributePrefix);
            if (npAttributeNode != null) {
                attributeName.addAll(npAttributeNode.getLeafParents());
            }
            if (vbConstraintNode != null) {
                attributeName.addAll(vbConstraintNode.getLeafParents());
            }

            String valueVariableName1 = getNextValueVariableName();
            String valueVariableName2 = getNextValueVariableName();
            ArrayList<QueryModel> qms = resolveLiteralNode(prepNP[1], valueVariableName2);
            ArrayList<SyntacticTreeNode> operatorNodes = new ArrayList<>();
            if (adjOp != null) {
                operatorNodes.add(adjOp);
            }
            operatorNodes.add(prepNP[0]);
            for (QueryModel qm : qms) {
                qm.getConstraints().add(new QueryConstraint(entityVariableName, lookupAttribute(attributeName), valueVariableName1, false));
                qm.getConstraints().add(new QueryConstraint(valueVariableName1, lookupOperator(operatorNodes), valueVariableName2, false));
            }
            res.addAll(qms);
        } else if (node.npQp) {
            ArrayList<SyntacticTreeNode> attributeName = new ArrayList<>(attributePrefix);
            SyntacticTreeNode qpNode = null;
            for (SyntacticTreeNode c : node.children) {
                if (c.value.equals("QP")) {
                    qpNode = c;
                } else {
                    attributeName.addAll(c.getLeafParents());
                }
            }
            if (qpNode != null) {
                SyntacticTreeNode jjrNode = null;
                SyntacticTreeNode inNode = null;
                String literalValue = "";
                for (SyntacticTreeNode c : qpNode.children) {
                    if (c.value.equals("JJR")) {
                        jjrNode = c;
                    } else if (c.value.equals("IN")) {
                        inNode = c;
                    } else {
                        String v = c.getLeafValues();
                        if (literalValue.length() > 0) {
                            literalValue += " ";
                        }
                        literalValue += v;
                    }
                }
                if (jjrNode != null && inNode != null) {
                    String operator = jjrNode.children.get(0).value + " " + inNode.children.get(0).value;
                    QueryModel qm = new QueryModel(entityVariableName, null);

                    String valueVariableName1 = getNextValueVariableName();
                    String valueVariableName2 = getNextValueVariableName();
                    qm.getConstraints().add(new QueryConstraint(entityVariableName, lookupAttribute(attributeName), valueVariableName1, false));
                    qm.getConstraints().add(new QueryConstraint(valueVariableName1, "lookupOperator(" + operator + ")", valueVariableName2, false));
                    qm.getConstraints().add(new QueryConstraint(valueVariableName2, "isLiteral", "literalValue(" + literalValue + ")", false));
                    res.add(qm);
                }
            }
        } else {
            //node can not be a value node
        }
        return res;
    }

    //receive a PP node and constructs a set of constraints with the entity called entityVariableName as subject of the constraints
    //using the IN and NP children of the node
    public ArrayList<QueryModel> resolvePPConstraint(SyntacticTreeNode node, String entityVariableName, ArrayList<SyntacticTreeNode> baseAttribute) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();

        if (!node.value.equals("PP")) {
            return res;
        }

        SyntacticTreeNode[] prepNP = extractInNpAdvpFromPp(node);

        if (prepNP != null) {
            ArrayList<SyntacticTreeNode> prep = new ArrayList<>();
            if (prepNP[2] != null) {
                prep.addAll(prepNP[2].getLeafParents());
            }
            prep.addAll(prepNP[0].getLeafParents());
            //create constraints with attributes, assuming that the values of the constraints are entities
            String newEntityName = getNextEntityVariableName();
            ArrayList<QueryModel> qmsE = resolveEntityNode(prepNP[1], newEntityName, true, true, new ArrayList<SyntacticTreeNode>());
            boolean annotationFound = false;
            for (QueryModel qm : qmsE) {
                for (QueryConstraint qc : qm.getConstraints()) {
                    if (qc.getSubjExpr().equals(newEntityName) && qc.getAttrExpr().equals("isEntity") && !qc.getValueExpr().startsWith("lookupEntity")) {
                        annotationFound = true;
                        break;
                    }
                }
                if (annotationFound) {
                    break;
                }
            }

            //if (!prepNP[0].getLeafValues().equals("of")) { //??? Why this condition? It make us miss the correct model for "What are the rivers of California?"
            ArrayList<SyntacticTreeNode> attributeName = new ArrayList<>(baseAttribute);
            attributeName.addAll(prep);
            for (QueryModel qm : qmsE) {
                qm.getConstraints().add(new QueryConstraint(entityVariableName, lookupAttribute(attributeName), newEntityName, false));
            }
            res.addAll(qmsE);
            //}
            //TODO: can we have value of value of entity or value of values of entity? is this too complex?
//            String newEntityName2 = getNextEntityVariableName();
//            ArrayList<QueryModel> qmsV = resolveValueNode(prepNP[1], newEntityName2, newEntityName, new ArrayList<SyntacticTreeNode>());
//            for (QueryModel qm : qmsV) {
//                qm.getConstraints().add(new QueryConstraint(entityVariableName, lookupAttribute(attributeName), newEntityName, false));
//            }
//            res.addAll(qmsV);

            //create constraints with attributes, assuming that the values of the constraints are literals
            if (!annotationFound || (annotationFound && prepNP[0].getLeafValues().equals("with"))) {
                ArrayList<QueryModel> qmsL = resolveLiteralConstraint(prepNP[1], entityVariableName, baseAttribute);
                res.addAll(qmsL);
            }
        } else {
            for (SyntacticTreeNode c : node.children) {
                res = combineQueryConstraints(res, resolvePPConstraint(c, entityVariableName, baseAttribute), true, true);
            }
        }
        return res;
    }

    //receive a VP node and constructs a set of constraints with the entity called entityVariableName as subject of the constraints
    //using the VB, PP, and NP children of the node
    public ArrayList<QueryModel> resolveVPConstraint(SyntacticTreeNode node, String entityVariableName, ArrayList<SyntacticTreeNode> baseAttribute) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();

        if (!node.value.equals("VP")) {
            return res;
        }

        //some cases are not covered: e.g.: trumpet players that "were bandleaders"
        SyntacticTreeNode[] verbPPNP = extractVerbPPNP(node);
        if (verbPPNP != null) {
            ArrayList<SyntacticTreeNode> attributeName = new ArrayList<>(baseAttribute);
            attributeName.addAll(verbPPNP[0].getLeafParents());
            if (verbPPNP[1] != null) {
                //TODO: add case for "spoken in Estonia" to "Estonia, speak, ans" to resolvePP
                res = resolvePPConstraint(verbPPNP[1], entityVariableName, attributeName);
                ArrayList<QueryModel> qmsL = resolveLiteralConstraint(node, entityVariableName, baseAttribute);
                res.addAll(qmsL);
            } else if (verbPPNP[2] != null) {
                String newEntityName = getNextEntityVariableName();
                ArrayList<QueryModel> qmsE = resolveEntityNode(verbPPNP[2], newEntityName, true, true, new ArrayList<SyntacticTreeNode>());
                for (QueryModel qm : qmsE) {
                    qm.getConstraints().add(new QueryConstraint(entityVariableName, lookupAttribute(attributeName), newEntityName, false));
                }
                res.addAll(qmsE);

                String newEntityName2 = getNextEntityVariableName();
                ArrayList<QueryModel> qmsV = resolveValueNode(verbPPNP[2], newEntityName2, newEntityName, new ArrayList<SyntacticTreeNode>());
                for (QueryModel qm : qmsV) {
                    qm.getConstraints().add(new QueryConstraint(entityVariableName, lookupAttribute(attributeName), newEntityName, false));
                }
                res.addAll(qmsV);

                String newValName = getNextValueVariableName();
                ArrayList<QueryModel> qmsL1 = resolveLiteralNode(verbPPNP[2], newValName);
                for (QueryModel qm : qmsL1) {
                    qm.getConstraints().add(new QueryConstraint(entityVariableName, lookupAttribute(attributeName), newValName, false));
                }
                res.addAll(qmsL1);

                ArrayList<QueryModel> qmsL2 = resolveLiteralConstraint(verbPPNP[2], entityVariableName, baseAttribute);
                res.addAll(qmsL2);
            }
        } else {
            for (SyntacticTreeNode c : node.children) {
                res = combineQueryConstraints(res, resolveVPConstraint(c, entityVariableName, baseAttribute), true, true);
            }
        }
        return res;
    }

    private String getNextEntityVariableName() {
        String res = "?ent" + entityCounter;
        entityCounter++;
        return res;
    }

    private String getNextValueVariableName() {
        String res = "?val" + valueCounter;
        valueCounter++;
        return res;
    }

    SyntacticTreeNode[] extractInNpAdvpFromPp(SyntacticTreeNode node) {
        SyntacticTreeNode prepNode = null;
        SyntacticTreeNode[] res = new SyntacticTreeNode[3];
        for (SyntacticTreeNode c : node.children) {
            if (c.value.equals("IN") || c.value.equals("TO")) {
                prepNode = c;
                break;
            }
        }
        if (prepNode == null) {
            System.out.println("IN/TO node not found");
            return null;
        } else {
            if (prepNode.children.size() != 1) {
                System.out.println("IN/TO node with " + prepNode.children.size() + " children");
                return null;
            } else {
                SyntacticTreeNode npNode = null;
                SyntacticTreeNode advpNode = null;
                for (SyntacticTreeNode c : node.children) {
                    if (c.value.equals("ADVP")) {
                        advpNode = c;
                    }
                    if (c.value.equals("NP") || c.value.equals("WHNP")) {
                        npNode = c;
                    }
                }

                if (npNode == null) {
                    System.out.println("PP node without NP child");
                    return null;
                } else {
                    res[0] = prepNode;
                    res[1] = npNode;
                    res[2] = advpNode;
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
                    res[0] = vbNode;
                    res[1] = ppNode;
                    res[2] = npNode;
                }
            }
        }
        return res;
    }

    private SyntacticTreeNode extractVPfromSBAR(SyntacticTreeNode node) {
        if (!node.value.equals("SBAR")) {
            return null;
        }
        SyntacticTreeNode s = null;
        for (SyntacticTreeNode c : node.children) {
            if (c.value.equals("S")) {
                s = c;
                break;
            }
        }
        if (s == null) {
            return null;
        }
        SyntacticTreeNode vp = null;
        for (SyntacticTreeNode c : s.children) {
            if (c.value.equals("VP")) {
                vp = c;
                break;
            }
        }
        return vp;
    }

    //this can be optimized by pre-grouping the constraints by value variable name
    private void updateAncestors(String nodeValue, HashMap<String, String> currentAncestors, Set<String> eBound, Set<String> vBound, ArrayList<QueryConstraint> constraints) {
        if (currentAncestors.containsKey(nodeValue)) {
            return;
        }

        String ancestor = null;
        for (QueryConstraint qc : constraints) {
            if (qc.getValueExpr().equals(nodeValue)) {
                if (ancestor != null) {
                    System.out.println("Found multiple ancestors");
                    return; //I don't know how to cope with this case, multiple ancestors?
                }
                ancestor = qc.getSubjExpr();
            }
        }
        if (ancestor != null) {
            if (!eBound.contains(ancestor) && !vBound.contains(ancestor)) {
                currentAncestors.put(nodeValue, ancestor);
            } else {
                updateAncestors(ancestor, currentAncestors, eBound, vBound, constraints);
                if (currentAncestors.containsKey(ancestor)) {
                    currentAncestors.put(nodeValue, currentAncestors.get(ancestor));
                }
            }
        }

    }

    private HashMap<String, String> computeUnboundAncestor(Set<String> eBound, Set<String> vBound, ArrayList<QueryConstraint> constraints) {
        HashMap<String, String> res = new HashMap<>();
        for (String ancestor : eBound) {
            updateAncestors(ancestor, res, eBound, vBound, constraints);
        }
        for (String ancestor : vBound) {
            updateAncestors(ancestor, res, eBound, vBound, constraints);
        }
        return res;
    }

    private void updateBoundVariables(String var, HashSet<String> boundVariables, HashSet<String> resultVariables, ArrayList<QueryConstraint> constraints) {
        if (boundVariables.contains(var)) {
            return;
        }
        for (QueryConstraint qc : constraints) {
            if (qc.getSubjExpr().equals(var)) {
                if (qc.getAttrExpr().equals("isEntity")
                        || qc.getAttrExpr().equals("isLiteral")
                        || qc.getAttrExpr().equals("rdf:type") && !resultVariables.contains(var)) {
                    boundVariables.add(var);
                    return;
                } else if (!resultVariables.contains(var)) {
                    String var2 = qc.getValueExpr();
                    if (var2.startsWith("?")) {
                        updateBoundVariables(var2, boundVariables, resultVariables, constraints);
                        if (boundVariables.contains(var2)) {
                            boundVariables.add(var);
                            return;
                        }
                    }
                }
            }
        }
    }

    private boolean reduceIsAttributes(QueryModel qm) {

        HashSet<String> resultVariables = new HashSet<>();
        if (qm.getEntityVariableName() != null) {
            resultVariables.add(qm.getEntityVariableName());
        }
        if (qm.getAttributeVariableName() != null) {
            resultVariables.add(qm.getAttributeVariableName());
        }

        HashMap<String, String> isEntity = new HashMap<>();
        HashMap<String, String> isLiteral = new HashMap<>();
        HashSet<String> literalConstraintsToReAdd = new HashSet<>();

        HashSet<String> boundVariables = new HashSet<>();
        for (QueryConstraint qc : qm.getConstraints()) {
            updateBoundVariables(qc.getSubjExpr(), boundVariables, resultVariables, qm.getConstraints());
        }

        ArrayList<QueryConstraint> newConstraints = new ArrayList<>();

        for (QueryConstraint qc : qm.getConstraints()) {
            if (qc.getAttrExpr().startsWith("isEntity")) {
                if (isEntity.containsKey(qc.getSubjExpr()) || isLiteral.containsKey(qc.getSubjExpr())) { //each variable can be at most one entity or one value
                    return false;
                }
                isEntity.put(qc.getSubjExpr(), qc.getValueExpr());
                if (qm.getEntityVariableName().equals(qc.getSubjExpr())) {
                    qm.setExampleEntity(qc.getValueExpr());
                }
            } else if (qc.getAttrExpr().startsWith("isLiteral")) {
                if (isEntity.containsKey(qc.getSubjExpr()) || isLiteral.containsKey(qc.getSubjExpr())) { //each variable can be at most one entity or one value
                    return false;
                }
                isLiteral.put(qc.getSubjExpr(), qc.getValueExpr());
            } else {
                newConstraints.add(qc.copy());
            }
        }

        HashMap<String, String> unboundAncestors = computeUnboundAncestor(isEntity.keySet(), isLiteral.keySet(), newConstraints);

        for (QueryConstraint qc : newConstraints) {
            if (boundVariables.contains(qc.getValueExpr())) { //the value is bounded
                //therefore, the subject cannot be a specific entity or value
                if (isEntity.containsKey(qc.getSubjExpr()) || isLiteral.containsKey(qc.getSubjExpr())) {
                    if (unboundAncestors.containsKey(qc.getSubjExpr())) {
                        qc.setSubjExpr(unboundAncestors.get(qc.getSubjExpr()));
                    } else {
                        return false;
                    }
                }
            }
            if (isEntity.containsKey(qc.getValueExpr())) {
                qc.setValueExpr(isEntity.get(qc.getValueExpr()));
            } else if (isLiteral.containsKey(qc.getValueExpr())) {
                if (!qc.getAttrExpr().startsWith("lookupAttribute")) {
                    qc.setValueExpr(isLiteral.get(qc.getValueExpr()));
                } else {
                    literalConstraintsToReAdd.add(qc.getValueExpr());
                }
            }
            if (isEntity.containsKey(qc.getSubjExpr())) {
                qc.setSubjExpr(isEntity.get(qc.getSubjExpr()));
            } else if (isLiteral.containsKey(qc.getSubjExpr()) && !qc.getAttrExpr().equals("isLiteral")) {
                return false;
            }
        }
        for (String var : literalConstraintsToReAdd) {
            newConstraints.add(new QueryConstraint(var, "isLiteral", isLiteral.get(var), false));
        }
        for (QueryConstraint qc : newConstraints) {
            if (resultVariables.contains(qc.getSubjExpr()) || resultVariables.contains(qc.getValueExpr())) {
                qm.setConstraints(newConstraints);
                return true;
            }
        }

        if (qm.getExampleEntity() != null && newConstraints.isEmpty()) {
            qm.setConstraints(newConstraints);
            return true;
        }

        return false;
    }

    public static void main(String[] args) throws Exception {
        Test.main(args);
    }
}
