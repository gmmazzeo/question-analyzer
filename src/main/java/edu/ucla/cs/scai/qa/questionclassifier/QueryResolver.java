/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cs.scai.qa.questionclassifier;

import edu.ucla.cs.scai.swim.qa.ontology.NamedEntityAnnotationResult;
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

    HashMap<String, String> namedEntitiesAnnotationMap = new HashMap<>();

    public QueryResolver(SyntacticTree tree) {
        this.tree = tree;
        for (NamedEntityAnnotationResult nar : tree.namedEntityAnnotations) {
            namedEntitiesAnnotationMap.put(nar.getSpot().replaceAll(" ", ""), nar.getNamedEntity().getUri());
        }
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
                res = combineQueryConstraints(res, resolveEntityNode(tree.labelledNodes.get(c.nodeLabel), c.entityVariableName, c.includeSpecificEntity, c.includeCategoryEntities, null, null), true, true);
            } else if (qc instanceof IValueNodeQueryConstraint) {
                IValueNodeQueryConstraint c = (IValueNodeQueryConstraint) qc;
                res = combineQueryConstraints(res, resolveValueNode(tree.labelledNodes.get(c.nodeLabel), c.entityVariableName, c.valueVariableName, c.attributePrefix), true, true);
            } else if (qc instanceof ISiblingsQueryConstraint && !res.isEmpty()) {
                ISiblingsQueryConstraint c = (ISiblingsQueryConstraint) qc;
                res = combineQueryConstraints(res, resolveSiblingConstraints(tree.labelledNodes.get(c.nodeLabel), c.entityVariableName, "", c.includeSelf), true, c.independent);
            } else if (qc instanceof IBoundThroughAttributeQueryConstraint) {
                IBoundThroughAttributeQueryConstraint c = (IBoundThroughAttributeQueryConstraint) qc;
                res = combineQueryConstraints(res, resolveBoundThroughAttributeConstraint(c), true, true);
            } else if (qc instanceof IOptionalCategoryQueryConstraint && !res.isEmpty()) {
                IOptionalCategoryQueryConstraint c = (IOptionalCategoryQueryConstraint) qc;
                res = combineQueryConstraints(res, resolveOptionalCategoryConstraint(c), true, false);
            }
        }
        System.out.println("Inital query models: " + res.size());
        for (Iterator<QueryModel> it = res.iterator(); it.hasNext();) {
            QueryModel qm = it.next();
            if (!reduceIsAttributes(qm) || qm.getConstraints().isEmpty() && qm.getExampleEntity() == null) {
                it.remove();
                System.out.println("Dropped:\n" + qm);

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
                    qmc.getConstraints().addAll(qm1.getConstraints());
                    qmc.getConstraints().addAll(qm2.getConstraints());
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
                    if (npNode != null) {
                        return null;
                    }
                    npNode = c;
                } else if (c.value.equals("PP")) {
                    if (ppNode != null) {
                        return null;
                    }
                    ppNode = c;
                }
            }
            if (npNode == null || ppNode == null) {
                return null; //node has not the structure we are looking for
            }

            SyntacticTreeNode[] prepNP = extractPPADVPprepNP(ppNode);

            if (!prepNP[0].lemma.equals("of")) {
                return null; //this was added to limit the possibile combination - in which cases nouns linked through a preposition different from "of" can represent attribute, entities or category names?
            }

            return prepNP;

        } else {
            return null;
        }
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null) {
            prefix = "";
        } else {
            prefix = prefix.trim();
        }
        if (prefix.length() > 0) {
            prefix += " ";
        }
        return prefix;
    }

    //resolves a NP/WHNP node, which can be either simple or compound
    private ArrayList<QueryModel> resolveEntityNode(SyntacticTreeNode node, String entityVariableName, boolean includeSpecificEntity, boolean includeCategoryEntities, String valuePrefix, String lemmaPrefix) throws Exception {
        valuePrefix = normalizePrefix(valuePrefix);
        lemmaPrefix = normalizePrefix(lemmaPrefix);

        ArrayList<QueryModel> res = new ArrayList<>();
        if (node.npSimple || node.whnpSimple) {
            if (includeSpecificEntity) {
                QueryModel qm = new QueryModel(entityVariableName, null);
                String entityName = node.getLeafValues();
                String possibileSpot = (valuePrefix + entityName).replaceAll(" ", "");
                if (namedEntitiesAnnotationMap.containsKey(possibileSpot)) {
                    qm.getConstraints().add(new QueryConstraint(entityVariableName, "isEntity", namedEntitiesAnnotationMap.get(possibileSpot), false));
                } else {
                    qm.getConstraints().add(new QueryConstraint(entityVariableName, "isEntity", "lookupEntity(" + valuePrefix + entityName + ")", false));
                }
                res.add(qm);
            }
            if (includeCategoryEntities) {
                QueryModel qm = new QueryModel(entityVariableName, null);
                String categoryName = node.getLeafLemmas();
                qm.getConstraints().add(new QueryConstraint(entityVariableName, "rdf:type", "lookupCategory(" + lemmaPrefix + categoryName + ")", false));
                res.add(qm);
            }

        } else if (node.npCompound || node.whnpCompound || node.value.equals("WHPP")) {
            //get the first NP child - TODO: what if the node has more NP children?
            SyntacticTreeNode np1 = null;
            for (SyntacticTreeNode c : node.children) {
                if (c.value.equals("NP") || c.value.equals("WHNP")) {
                    np1 = c;
                    break;
                }
            }
            if (np1 == null) {
                return res; //node has not the structure we are looking for
            }

            ArrayList<QueryModel> qmsMainEntity = resolveEntityNode(np1, entityVariableName, includeSpecificEntity, includeCategoryEntities, valuePrefix, lemmaPrefix);
            ArrayList<QueryModel> qmsConstraints = resolveSiblingConstraints(np1, entityVariableName, "", false);
            res = combineQueryConstraints(qmsMainEntity, qmsConstraints, true, false);

            SyntacticTreeNode[] npExt = npExtension(node);
            if (npExt != null) {
                res.addAll(resolveEntityNode(npExt[1], entityVariableName, includeSpecificEntity, includeCategoryEntities, valuePrefix + np1.getLeafValues() + " " + npExt[0].value, lemmaPrefix + np1.getLeafLemmas() + " " + npExt[0].lemma));
            }
        }
        return res;
    }

    //return the literal value represented by a node
    private ArrayList<QueryModel> resolveLiteralNode(SyntacticTreeNode node, String valueVariableName) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();
        if (node.npSimple || node.whnpSimple) {
            QueryModel qm = new QueryModel(valueVariableName, null);
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
        attributePrefix = normalizePrefix(attributePrefix);
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

            SyntacticTreeNode[] prepNP = extractPPADVPprepNP(ppEntityNode);

            if (prepNP == null) {
                return res;
            }

            String attributeName = npAttributeNode.getLeafLemmas();

            ArrayList<QueryModel> qms1 = resolveEntityNode(prepNP[1], entityVariableName, true, true, null, null);
            for (QueryModel qm : qms1) {
                qm.getConstraints().add(new QueryConstraint(entityVariableName, "lookupAttribute(" + attributePrefix + attributeName + "(s) [" + prepNP[0].lemma + "])", valueVariableName, false));
            }
            res.addAll(qms1);

            String newEntityVariable = getNextEntityVariableName();
            ArrayList<QueryModel> qms2 = resolveValueNode(prepNP[1], entityVariableName, newEntityVariable, "");
            for (QueryModel qm : qms2) {
                qm.getConstraints().add(new QueryConstraint(newEntityVariable, "lookupAttribute(" + attributePrefix + attributeName + "(s) [" + prepNP[0].lemma + "])", valueVariableName, false));
            }
            res.addAll(qms2);

            SyntacticTreeNode[] npExt = npExtension(node);
            if (npExt != null) {
                res.addAll(resolveValueNode(npExt[1], entityVariableName, valueVariableName, attributePrefix + attributeName + " " + npExt[0].value));
            }

        } else {
            //node can not be a value node
        }
        return res;
    }

    //receive a node and constructs a set of constraints with the entity called entityVariableName as subject of the constraints
    //using the PP and VP siblings of the node
    ArrayList<QueryModel> resolveSiblingConstraints(SyntacticTreeNode node, String entityVariableName, String baseAttributeName, boolean includeSelf) throws Exception {
        if (baseAttributeName == null) {
            baseAttributeName = "";
        } else {
            baseAttributeName = baseAttributeName.trim();
            if (baseAttributeName.length() > 0) {
                baseAttributeName += " ";
            }
        }
        ArrayList<QueryModel> res = new ArrayList<>();
        for (SyntacticTreeNode c : node.parent.children) {
            if (c == node && !includeSelf) {
                continue;
            }

            if (c.value.equals("PP")) {
                res = combineQueryConstraints(res, resolvePPConstraint(c, entityVariableName, baseAttributeName), true, true);
            } else if (c.value.equals("VP")) {
                res = combineQueryConstraints(res, resolveVPConstraint(c, entityVariableName, baseAttributeName), true, true);
            }

        }
        return res;
    }

    private ArrayList<QueryModel> resolveBoundThroughAttributeConstraint(IBoundThroughAttributeQueryConstraint c) {
        ArrayList<QueryModel> res = new ArrayList<>();
        QueryModel qm = new QueryModel(c.getEntityVariableName(), c.getValueVariableName());
        res.add(qm);
        if (c.getAttributeNodes().length == 1 && !tree.labelledNodes.containsKey(c.getAttributeNodes()[0])) { //it is a reserved word - e.g. date
            qm.getConstraints().add(new QueryConstraint(c.entityVariableName, "defaultAttribute(" + c.getAttributeNodes()[0] + ")", c.valueVariableName, c.optional));
        } else {
            String attributeName = "";
            for (String a : c.attributeNodes) {
                if (attributeName.length() > 0) {
                    attributeName += " ";
                }
                attributeName += tree.labelledNodes.get(a).getLeafLemmas();
            }

            SyntacticTreeNode ppEntityNode = null;
            for (String a : c.attributeNodes) {
                if (tree.labelledNodes.get(a).value.equals("PP")) {
                    if (ppEntityNode != null) {
                        System.out.println("Warning: node with two or more PP children");
                    }
                    ppEntityNode = tree.labelledNodes.get(a);
                }
            }

            String prep = "";

            if (ppEntityNode != null) {
                SyntacticTreeNode[] prepNP = extractPPADVPprepNP(ppEntityNode);

                if (prepNP != null) {
                    prep = prepNP[0].lemma;
                }
            }

            qm.getConstraints().add(new QueryConstraint(c.entityVariableName, "lookupAttribute(" + attributeName + prep + (c.typeName.isEmpty() ? "" : " " + c.typeName) + ")", c.valueVariableName, c.optional));
        }
        return res;
    }

    private ArrayList<QueryModel> resolveOptionalCategoryConstraint(IOptionalCategoryQueryConstraint c) throws Exception {
        ArrayList<QueryModel> res = resolveEntityNode(tree.labelledNodes.get(c.getNodeLabel()), c.entityVariableName, false, true, null, null);
        for (QueryModel qm : res) {
            for (QueryConstraint qc : qm.getConstraints()) {
                qc.setOptional(true);
            }
        }
        return res;
    }

    //construct the query model from a NP node containing a simple NP node representing an attribute, and a PP node where the preposition is the operator and the NP child represents the literal
    private ArrayList<QueryModel> resolveLiteralConstraint(SyntacticTreeNode node, String entityVariableName) throws Exception {
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

            SyntacticTreeNode[] prepNP = extractPPADVPprepNP(ppConstraintNode);

            if (prepNP == null) {
                return res;
            }

            String attributeName = npAttributeNode != null ? npAttributeNode.getLeafLemmas() : vbConstraintNode.children.get(0).lemma;

            String valueVariableName1 = getNextValueVariableName();
            String valueVariableName2 = getNextValueVariableName();
            ArrayList<QueryModel> qms = resolveLiteralNode(prepNP[1], valueVariableName2);
            for (QueryModel qm : qms) {
                qm.getConstraints().add(new QueryConstraint(entityVariableName, "lookupAttribute(" + attributeName + ")", valueVariableName1, false));
                qm.getConstraints().add(new QueryConstraint(valueVariableName1, "lookupOperator(" + prepNP[0].lemma + ")", valueVariableName2, false));
            }
            res.addAll(qms);
        } else if (node.npQp) {
            String attributeName = node.getLeafLemmas();
            SyntacticTreeNode qpNode = null;
            for (SyntacticTreeNode c : node.children) {
                if (c.value.equals("QP")) {
                    qpNode = c;
                    break;
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
                    qm.getConstraints().add(new QueryConstraint(entityVariableName, "lookupAttribute(" + attributeName + ")", valueVariableName1, false));
                    qm.getConstraints().add(new QueryConstraint(valueVariableName1, "lookupOperator(" + operator + ")", valueVariableName2, false));
                    qm.getConstraints().add(new QueryConstraint(valueVariableName2, "isVal", "literalValue(" + literalValue + ")", false));
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
    public ArrayList<QueryModel> resolvePPConstraint(SyntacticTreeNode node, String entityVariableName, String baseAttribute) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();

        if (!node.value.equals("PP")) {
            return res;
        }

        SyntacticTreeNode[] prepNP = extractPPADVPprepNP(node);
        String prep = "";
        if (prepNP != null) {
            prep = prepNP[0].lemma;
            if (prepNP[2] != null) {
                prep = prepNP[2].getLeafLemmas() + " " + prep;
            }
        }
        
        if (prepNP != null) {
            //create constraints with attributes, assuming that the values of the constraints are entities
            String newEntityName = getNextEntityVariableName();
            ArrayList<QueryModel> qmsE = resolveEntityNode(prepNP[1], newEntityName, true, true, null, null);
            for (QueryModel qm : qmsE) {
                qm.getConstraints().add(new QueryConstraint(entityVariableName, "lookupAttribute(" + baseAttribute + prep + ")", newEntityName, false));
            }
            res.addAll(qmsE);

            String newEntityName2 = getNextEntityVariableName();
            ArrayList<QueryModel> qmsV = resolveValueNode(prepNP[1], newEntityName2, newEntityName, "");
            for (QueryModel qm : qmsV) {
                qm.getConstraints().add(new QueryConstraint(entityVariableName, "lookupAttribute(" + baseAttribute + prep + ")", newEntityName, false));
            }
            res.addAll(qmsV);

            //create constraints with attributes, assuming that the values of the constraints are literals
            ArrayList<QueryModel> qmsL = resolveLiteralConstraint(prepNP[1], entityVariableName);
            res.addAll(qmsL);
        } else {
            for (SyntacticTreeNode c : node.children) {
                res = combineQueryConstraints(res, resolvePPConstraint(c, entityVariableName, baseAttribute), true, true);
            }
        }
        return res;
    }

    //receive a VP node and constructs a set of constraints with the entity called entityVariableName as subject of the constraints
    //using the VB, PP, and NP children of the node
    public ArrayList<QueryModel> resolveVPConstraint(SyntacticTreeNode node, String entityVariableName, String baseAttribute) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();

        if (!node.value.equals("VP")) {
            return res;
        }

        SyntacticTreeNode[] verbPPNP = extractVerbPPNP(node);
        if (verbPPNP != null) {
            if (verbPPNP[1] != null) {
                res = combineQueryConstraints(res, resolvePPConstraint(verbPPNP[1], entityVariableName, baseAttribute + verbPPNP[0].lemma + " "), true, true);
//                SyntacticTreeNode[] prepNp = extractPPprepNP(verbPPNP[1]);
//                if (prepNp != null) {
//                    String newEntityName = getNextEntityVariableName();
//                    ArrayList<QueryModel> qmsE = resolveEntityNode(prepNp[1], newEntityName, true, true, null, null);
//                    for (QueryModel qm : qmsE) {
//                        qm.getConstraints().add(new QueryConstraint(entityVariableName, "lookupAttribute(" + baseAttribute + verbPPNP[0].lemma + " " + prepNp[0].lemma + ")", newEntityName, false));
//                    }
//                    res.addAll(qmsE);
//
//                    String newEntityName2 = getNextEntityVariableName();
//                    ArrayList<QueryModel> qmsV = resolveValueNode(prepNp[1], newEntityName2, newEntityName, "");
//                    for (QueryModel qm : qmsV) {
//                        qm.getConstraints().add(new QueryConstraint(entityVariableName, "lookupAttribute(" + baseAttribute + verbPPNP[0].lemma + " " + prepNp[0].lemma + ")", newEntityName, false));
//                    }
//                    res.addAll(qmsV);
//
//                    ArrayList<QueryModel> qmsL = resolveLiteralConstraint(node, entityVariableName);
//                    res.addAll(qmsL);
//                }
            } else if (verbPPNP[2] != null) {
                String newEntityName = getNextEntityVariableName();
                ArrayList<QueryModel> qmsE = resolveEntityNode(verbPPNP[2], newEntityName, true, true, null, null);
                for (QueryModel qm : qmsE) {
                    qm.getConstraints().add(new QueryConstraint(entityVariableName, "lookupAttribute(" + baseAttribute + verbPPNP[0].lemma + ")", newEntityName, false));
                }
                res.addAll(qmsE);

                String newEntityName2 = getNextEntityVariableName();
                ArrayList<QueryModel> qmsV = resolveValueNode(verbPPNP[2], newEntityName2, newEntityName, "");
                for (QueryModel qm : qmsV) {
                    qm.getConstraints().add(new QueryConstraint(entityVariableName, "lookupAttribute(" + baseAttribute + verbPPNP[0].lemma + ")", newEntityName, false));
                }
                res.addAll(qmsV);

                String newValName = getNextValueVariableName();
                ArrayList<QueryModel> qmsL1 = resolveLiteralNode(verbPPNP[2], newValName);
                for (QueryModel qm : qmsL1) {
                    qm.getConstraints().add(new QueryConstraint(entityVariableName, "lookupAttribute(" + verbPPNP[0].lemma + ")", newValName, false));
                }
                res.addAll(qmsL1);

                ArrayList<QueryModel> qmsL2 = resolveLiteralConstraint(verbPPNP[2], entityVariableName);
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

    SyntacticTreeNode[] extractPPADVPprepNP(SyntacticTreeNode node) {
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
                    if (c.value.equals("NP")) {
                        npNode = c;
                    }
                }
                
                if (npNode == null) {
                    System.out.println("PP node without NP child");
                    return null;
                } else {
                    res[0] = prepNode.children.get(0);
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
                    res[0] = vbNode.children.get(0);
                    res[1] = ppNode;
                    res[2] = npNode;
                }
            }
        }
        return res;
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
                        || qc.getAttrExpr().equals("isVal")
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
        HashMap<String, String> isVal = new HashMap<>();

        HashSet<String> boundVariables = new HashSet<>();
        for (QueryConstraint qc : qm.getConstraints()) {
            updateBoundVariables(qc.getSubjExpr(), boundVariables, resultVariables, qm.getConstraints());
        }

        ArrayList<QueryConstraint> newConstraints = new ArrayList<>();

        for (QueryConstraint qc : qm.getConstraints()) {
            if (qc.getAttrExpr().startsWith("isEntity")) {
                if (isEntity.containsKey(qc.getSubjExpr()) || isVal.containsKey(qc.getSubjExpr())) { //each variable can be at most one entity or one value
                    return false;
                }
                isEntity.put(qc.getSubjExpr(), qc.getValueExpr());
                if (qm.getEntityVariableName().equals(qc.getSubjExpr())) {
                    qm.setExampleEntity(qc.getValueExpr());
                }
            } else if (qc.getAttrExpr().startsWith("isVal")) {
                if (isEntity.containsKey(qc.getSubjExpr()) || isVal.containsKey(qc.getSubjExpr())) { //each variable can be at most one entity or one value
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
        for (QueryConstraint qc : newConstraints) {
            if (resultVariables.contains(qc.getSubjExpr()) || resultVariables.contains(qc.getValueExpr())) {
                qm.setConstraints(newConstraints);
                return true;
            }
        }

        if (qm.getExampleEntity() != null) {
            qm.setConstraints(newConstraints);
            return true;
        }

        return false;
    }
}
