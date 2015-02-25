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

    SyntacticTree tree;

    static final Ontology ontology;

    static {
        ontology = DBpediaOntology.getInstance();
    }

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
        } else {
            if (qms1.isEmpty()) {
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
        }
        return res;
    }

    private ArrayList<QueryModel> resolveIQueryModel(IQueryModel qm) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();
        for (IQueryConstraint qc : qm.getConstraints()) {
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
            String entityName = node.getLeafValues();

            if (includeSpecificEntity) {
                QueryModel qm = new QueryModel();
                qm.getConstraints().add(new QueryConstraint(entityVariableName, "is", "lookupEntity(" + entityName + ")", false));
                res.add(qm);
            }

            if (includeCategoryEntities) {
                String categoryName = node.getLeafLemmas();
                QueryModel qm = new QueryModel();
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

    //construct the query model from a NP node containing a simple NP nod,e representing an attribute, and a PP node where the preposition is of and the NP child represents the entity
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

            ArrayList<QueryModel> qms = resolveEntityNode(prepNP[1], entityVariableName, true, true);

            QueryConstraint qc = new QueryConstraint(entityVariableName, "lookupAttribute(" + attributeName + " " + prepNP[0].lemma + ")", valueVariableName, false);

            for (QueryModel qm : qms) {
                qm.getConstraints().add(qc);
                res.add(qm);
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

    public ArrayList<QueryModel> resolvePPConstraint(SyntacticTreeNode node, String entityVariableName, String baseAttributeName) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();
        SyntacticTreeNode[] prepNp = extractPPprepNP(node);
        if (prepNp != null) {
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

            //TODO: check if it makes sense to combine the baseAttribute name with the revolveValueNode (e.g., "in the state of New York")
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
            }
        }

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
                if (ppNode == null) {
                    System.out.println("PP node not found");
                } else {
                    res[1] = ppNode;
                }

                SyntacticTreeNode npNode = null;
                for (SyntacticTreeNode c : node.children) {
                    if (c.value.equals("NP")) {
                        npNode = c;
                        break;
                    }
                }
                if (npNode == null) {
                    System.out.println("NP node not found");
                } else {
                    res[2] = npNode;
                }

                if (npNode == null && ppNode == null) {
                    return null;
                }
            }
        }
        return res;
    }
}