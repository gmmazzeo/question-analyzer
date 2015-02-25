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

/**
 *
 * @author Giuseppe M. Mazzeo <mazzeo@cs.ucla.edu>
 */
public class QueryResolver {

    private int entityCounter;

    static final Ontology ontology;

    static {
        ontology = DBpediaOntology.getInstance();
    }

    HashMap<SyntacticTreeNode, ArrayList<QueryModel>> ppCache = new HashMap<>();
    HashMap<SyntacticTreeNode, String> ppCacheLabel = new HashMap<>();

    public ArrayList<QueryModel> resolveQueries(SyntacticTree tree, ArrayList<PennTreebankPattern> patterns) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();
        for (PennTreebankPattern pattern : patterns) {
            res.addAll(resolveIQueryModels(tree, pattern));
        }
        return res;
    }

    private ArrayList<QueryModel> combineQueryConstraints(ArrayList<QueryModel> qms1, ArrayList<QueryModel> qms2, boolean includeQms1IfQms2isEmpty) {
        ArrayList<QueryModel> res = new ArrayList<>();
        if (qms2.isEmpty()) {
            if (includeQms1IfQms2isEmpty) {
                res.addAll(qms1);
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

    public ArrayList<QueryModel> resolveIQueryModel(SyntacticTree tree, IQueryModel qm) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();
        for (IQueryConstraint qc : qm.getConstraints()) {
            if (qc instanceof IEntityNodeQueryConstraint) {
                IEntityNodeQueryConstraint c = (IEntityNodeQueryConstraint) qc;
                for (QueryModel sqm : resolveEntityNode(tree.labelledNodes.get(c.nodeLabel), c.entityVariableName, c.getIncludeSpecificEntity(), c.getIncludeCategorieEntities())) {
                    res.add(sqm);
                }
            } else if (qc instanceof IValueNodeQueryConstraint) {
                IValueNodeQueryConstraint c = (IValueNodeQueryConstraint) qc;
                for (QueryModel sqm : resolveValueNode(tree.labelledNodes.get(c.nodeLabel), c.entityVariableName, c.getValueVariableName(), c.getAttributePrefix())) {
                    res.add(sqm);
                }
            } else if (qc instanceof ISiblingsQueryConstraint) {
                ISiblingsQueryConstraint c = (ISiblingsQueryConstraint) qc;
                if (res.isEmpty()) {
                    throw new Exception("Cannot extend conditions with siblings of " + c.nodeLabel + ". Current constraint set is empty");
                }
                res = combineQueryConstraints(res, resolveSiblingConstraints(tree.labelledNodes.get(c.nodeLabel), c.entityVariableName), true);
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
                    res = combineQueryConstraints(res, qml, false);
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
                    res = combineQueryConstraints(res, eqms, true);
                }
            }
        }
        return res;
    }

    public ArrayList<QueryModel> resolveIQueryModels(SyntacticTree tree, PennTreebankPattern pattern) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();
        for (IQueryModel qm : pattern.iQueryModels) {
            try {
                res.addAll(resolveIQueryModel(tree, qm));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return res;
    }

    ArrayList<QueryModel> resolveEntityNode(SyntacticTreeNode root, String entityVariableName, boolean includeSpecificEntity, boolean includeCategoryEntities) throws Exception {

        ArrayList<QueryModel> res = new ArrayList<>();
        if (root.npSimple || root.whnpSimple) {
            String entityName = root.getLeafValues();

            if (includeSpecificEntity) {
                QueryModel qm = new QueryModel();
                qm.getConstraints().add(new QueryConstraint(entityVariableName, "is", "lookupEntity(" + entityName + ")", false));
                res.add(qm);
            }

            if (includeCategoryEntities) {
                String categoryName = root.getLeafLemmas();
                QueryModel qm = new QueryModel();
                qm.getConstraints().add(new QueryConstraint(entityVariableName, "rdf:type", "lookupCategory(" + categoryName + ")", false));
                res.add(qm);
            }
        } else if (root.npCompound || root.whnpCompound) {
            res.addAll(resolveEntityNPPPVP(root, entityVariableName, includeSpecificEntity, includeCategoryEntities));
        }
        return res;
    }

    ArrayList<QueryModel> resolveValueNode(SyntacticTreeNode root, String entityVariableName, String valueVariableName, String attributePrefix) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();
        res.addAll(resolveValueNPPPVP(root, entityVariableName, valueVariableName));
        return res;
    }

    //construct the query model from a NP node containing a NP node representing a set of entities and a set of PP and VP nodes representing conditions
    ArrayList<QueryModel> resolveEntityNPPPVP(SyntacticTreeNode node, String entityVariableName, boolean includeSpecificEntity, boolean includeCategoryEntities) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();
        if (node.npCompound) {
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

            ArrayList<QueryModel> qm0s = resolveEntityNode(np1, entityVariableName, includeSpecificEntity, includeCategoryEntities);

            for (SyntacticTreeNode c : node.children) {
                if (c.value.equals("PP")) {
                    SyntacticTreeNode[] prepNp = extractPPprepNP(c);
                    if (prepNp != null) {
                        String newEntityName = getNextEntityVariableName();
                        for (QueryModel qm : resolveEntityNode(prepNp[1], newEntityName, true, true)) {
                            for (QueryModel qm0 : qm0s) {
                                QueryModel qm1 = new QueryModel();
                                qm1.getConstraints().addAll(qm0.getConstraints());
                                qm1.getConstraints().add(new QueryConstraint(entityVariableName, "lookupAttribute(" + prepNp[0].lemma + ")", newEntityName, false));
                                qm1.getConstraints().addAll(qm.getConstraints());
                                res.add(qm1);
                            }
                        }

                        String newEntityName2 = getNextEntityVariableName();
                        for (QueryModel qm : resolveValueNode(prepNp[1], newEntityName2, newEntityName, "")) {
                            for (QueryModel qm0 : qm0s) {
                                QueryModel qm1 = new QueryModel();
                                qm1.getConstraints().addAll(qm0.getConstraints());
                                qm1.getConstraints().add(new QueryConstraint(entityVariableName, "lookupAttribute(" + prepNp[0].lemma + ")", newEntityName, false));
                                qm1.getConstraints().addAll(qm.getConstraints());
                                res.add(qm1);
                            }
                        }
                    }
                } else if (c.value.equals("VP")) {
                    SyntacticTreeNode[] verbPP = extractVerbPP(c);
                    if (verbPP != null) {
                        SyntacticTreeNode[] prepNp = extractPPprepNP(verbPP[1]);
                        if (prepNp != null) {
                            String newEntityName = getNextEntityVariableName();
                            for (QueryModel qm : resolveEntityNode(prepNp[1], newEntityName, true, true)) {
                                for (QueryModel qm0 : qm0s) {
                                    QueryModel qm1 = new QueryModel();
                                    qm1.getConstraints().addAll(qm0.getConstraints());
                                    qm1.getConstraints().add(new QueryConstraint(entityVariableName, "lookupAttribute(" + verbPP[0].lemma + " " + prepNp[0].lemma + ")", newEntityName, false));
                                    qm1.getConstraints().addAll(qm.getConstraints());
                                    res.add(qm1);
                                }
                            }

                            String newEntityName2 = getNextEntityVariableName();
                            for (QueryModel qm : resolveValueNode(prepNp[1], newEntityName2, newEntityName, "")) {
                                for (QueryModel qm0 : qm0s) {
                                    QueryModel qm1 = new QueryModel();
                                    qm1.getConstraints().addAll(qm0.getConstraints());
                                    qm1.getConstraints().add(new QueryConstraint(entityVariableName, "lookupAttribute(" + verbPP[0].lemma + " " + prepNp[0].lemma + ")", newEntityName, false));
                                    qm1.getConstraints().addAll(qm.getConstraints());
                                    res.add(qm1);
                                }
                            }
                        }

                    }
                }
            }
        } else {
            //node can not be a value node
        }
        return res;
    }

    //construct the query model from a NP node containing a NP node representing an attribute and a set of PP and VP nodes representing the domain of entityVariableName
    ArrayList<QueryModel> resolveValueNPPPVP(SyntacticTreeNode node, String entityVariableName, String valueVariableName) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();
        if (node.npCompound) {
            //get the first simple NP child - TODO: what if the node has more NP children?
            SyntacticTreeNode np1 = null;
            for (SyntacticTreeNode c : node.children) {
                if (c.npSimple) {
                    np1 = c;
                    break;
                }
            }
            if (np1 == null) {
                return res; //node has not the structure we are looking for
            }

            String attributeName = np1.getLeafLemmas();

            for (SyntacticTreeNode c : node.children) {
                if (c.value.equals("PP")) {
                    SyntacticTreeNode[] prepNp = extractPPprepNP(c);
                    if (prepNp != null) {
                        for (QueryModel qm : resolveEntityNode(prepNp[1], entityVariableName, true, true)) {
                            QueryModel qm1 = new QueryModel();
                            qm1.getConstraints().add(new QueryConstraint(entityVariableName, "lookupAttribute(" + attributeName + " " + prepNp[0].lemma + ")", valueVariableName, false));
                            qm1.getConstraints().addAll(qm.getConstraints());
                            res.add(qm1);
                        }

                        String newEntityName = getNextEntityVariableName();
                        for (QueryModel qm : resolveValueNode(prepNp[1], newEntityName, entityVariableName, "")) {
                            QueryModel qm1 = new QueryModel();
                            qm1.getConstraints().add(new QueryConstraint(entityVariableName, "lookupAttribute(" + attributeName + " " + prepNp[0].lemma + ")", valueVariableName, false));
                            qm1.getConstraints().addAll(qm.getConstraints());
                            res.add(qm1);
                        }
                    }
                } else if (c.value.equals("VP")) { //TODO: check whether this possibility makes sense
                    SyntacticTreeNode[] verbPP = extractVerbPP(c);
                    if (verbPP != null) {
                        SyntacticTreeNode[] prepNp = extractPPprepNP(verbPP[1]);
                        if (prepNp != null) {
                            for (QueryModel qm : resolveEntityNode(prepNp[1], entityVariableName, true, true)) {
                                QueryModel qm1 = new QueryModel();
                                qm1.getConstraints().add(new QueryConstraint(entityVariableName, "lookupAttribute(" + attributeName + " " + verbPP[0].lemma + " " + prepNp[0].lemma + ")", valueVariableName, false));
                                qm1.getConstraints().addAll(qm.getConstraints());
                                res.add(qm1);
                            }

                            String newEntityName = getNextEntityVariableName();
                            for (QueryModel qm : resolveValueNode(prepNp[1], newEntityName, entityVariableName, "")) {
                                QueryModel qm1 = new QueryModel();
                                qm1.getConstraints().add(new QueryConstraint(entityVariableName, "lookupAttribute(" + attributeName + " " + verbPP[0].lemma + " " + prepNp[0].lemma + ")", valueVariableName, false));
                                qm1.getConstraints().addAll(qm.getConstraints());
                                res.add(qm1);
                            }
                        }
                    }
                }
            }
        } else {
            //node can not be a value node
        }
        return res;
    }

    ArrayList<QueryModel> copyAndReplaceEntityVariableName(ArrayList<QueryModel> qms, String oldName, String newName) {
        ArrayList<QueryModel> qms2 = new ArrayList<>();
        for (QueryModel qm : qms) {
            QueryModel qm2 = new QueryModel();
            qms2.add(qm2);
            for (QueryConstraint qc : qm.getConstraints()) {
                String eName = qc.getValueExpr();
                QueryConstraint qc2 = new QueryConstraint(eName.equals(oldName) ? newName : eName, qc.getAttrExpr(), qc.getValueExpr(), qc.isOptional());
                qm2.getConstraints().add(qc2);
            }
        }
        return qms2;
    }

    //receive a node (root) and constructs a set of contraints with the entity called entityVariableName as subject of the constraints
    //using the PP and VP siblings of the node
    ArrayList<QueryModel> resolveSiblingConstraints(SyntacticTreeNode root, String entityVariableName) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();
        for (SyntacticTreeNode c : root.parent.children) {
            if (c == root) {
                continue;
            }

            if (c.value.equals("PP")) {
                SyntacticTreeNode[] prepNp = extractPPprepNP(c);
                if (prepNp != null) {
                    String newEntityName = getNextEntityVariableName();
                    ArrayList<QueryModel> qms = resolveEntityNode(prepNp[1], newEntityName, true, true);
                    for (QueryModel qm : qms) {
                        qm.getConstraints().add(new QueryConstraint(entityVariableName, "lookupAttribute(" + prepNp[0].lemma + ")", newEntityName, false));
                    }
                    ArrayList<QueryModel> res1 = combineQueryConstraints(res, qms, true);

                    String newEntityName2 = getNextEntityVariableName();
                    qms = resolveValueNode(prepNp[1], newEntityName2, newEntityName, "");
                    for (QueryModel qm : qms) {
                        qm.getConstraints().add(new QueryConstraint(entityVariableName, "lookupAttribute(" + prepNp[0].lemma + ")", newEntityName, false));
                    }
                    ArrayList<QueryModel> res2 = combineQueryConstraints(res, qms, false);

                    res1.addAll(res2);

                    res = res1;
                }
            } else if (c.value.equals("VP")) {
                SyntacticTreeNode[] verbPP = extractVerbPP(c);
                if (verbPP != null) {
                    SyntacticTreeNode[] prepNp = extractPPprepNP(verbPP[1]);
                    if (prepNp != null) {
                        String newEntityName = getNextEntityVariableName();
                        ArrayList<QueryModel> qms = resolveEntityNode(prepNp[1], newEntityName, true, true);
                        for (QueryModel qm : qms) {
                            qm.getConstraints().add(new QueryConstraint(entityVariableName, "lookupAttribute(" + verbPP[0].lemma + " " + prepNp[0].lemma + ")", newEntityName, false));
                        }
                        ArrayList<QueryModel> res1 = combineQueryConstraints(res, qms, true);

                        String newEntityName2 = getNextEntityVariableName();
                        qms = resolveValueNode(prepNp[1], newEntityName2, newEntityName, "");
                        for (QueryModel qm : qms) {
                            qm.getConstraints().add(new QueryConstraint(entityVariableName, "lookupAttribute(" + verbPP[0].lemma + " " + prepNp[0].lemma + ")", newEntityName, false));
                        }
                        ArrayList<QueryModel> res2 = combineQueryConstraints(res, qms, false);

                        res1.addAll(res2);

                        res = res1;
                    }

                }
            }

        }
        return res;
    }

    public ArrayList<QueryConstraint> solvePPConstraints(SyntacticTreeNode node, String entityVariableName) {
        ArrayList<QueryConstraint> res = new ArrayList<>();
        return res;
    }

    public ArrayList<QueryConstraint> solveVPConstraints(SyntacticTreeNode node, String entityVariableName) {
        ArrayList<QueryConstraint> res = new ArrayList<>();
        return res;
    }

    private String getNextEntityVariableName() {
        String res = "e" + entityCounter;
        entityCounter++;
        return res;
    }

    SyntacticTreeNode[] extractPPprepNP(SyntacticTreeNode node) {
        SyntacticTreeNode inNode = null;
        SyntacticTreeNode[] res = new SyntacticTreeNode[2];
        for (SyntacticTreeNode c : node.children) {
            if (c.value.equals("IN")) {
                inNode = c;
                break;
            }
        }
        if (inNode == null) {
            System.out.println("IN node not found");
            return null;
        } else {
            if (inNode.children.size() != 1) {
                System.out.println("IN node with " + inNode.children.size() + " children");
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

    SyntacticTreeNode[] extractVerbPP(SyntacticTreeNode node) {
        SyntacticTreeNode vbNode = null;
        SyntacticTreeNode[] res = new SyntacticTreeNode[3];
        for (SyntacticTreeNode c : node.children) {
            if (c.value.equals("VBG") || c.value.equals("VBN")) {
                vbNode = c;
                break;
            }
        }
        if (vbNode == null) {
            System.out.println("VBG|VBN node not found");
            return null;
        } else {
            if (vbNode.children.size() != 1) {
                System.out.println("VBG|VBN node with " + vbNode.children.size() + " children");
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
                if (ppNode == null) {
                    System.out.println("PP node not found");
                    return null;
                } else {
                    res[1] = ppNode;
                }

            }
        }
        return res;
    }
}
