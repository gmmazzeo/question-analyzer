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
            res.addAll(resolveQueries(tree, pattern));
        }
        return res;
    }

    public ArrayList<QueryModel> resolveQueries(SyntacticTree tree, PennTreebankPattern pattern) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();
        for (IQueryModel qm : pattern.iQueryModels) {
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
                    ArrayList<QueryModel> extRes = new ArrayList<>();
                    for (QueryModel sqm : resolveSiblingConstraints(tree.labelledNodes.get(c.nodeLabel), c.entityVariableName)) {
                        for (QueryModel qmt : res) {
                            QueryModel extQm = new QueryModel();
                            extQm.getConstraints().addAll(qmt.getConstraints());
                            extQm.getConstraints().addAll(sqm.getConstraints());
                            extRes.add(extQm);
                        }
                    }
                    res = extRes;
                } else if (qc instanceof IBoundThroughAttributeQueryConstraint) {
                    IBoundThroughAttributeQueryConstraint c = (IBoundThroughAttributeQueryConstraint) qc;
                    if (res.isEmpty()) {
                        throw new Exception("Cannot extend conditions with bounds of " + Arrays.toString(c.attributeNodes) + ". Current constraint set is empty");
                    }
                    ArrayList<QueryModel> extRes = new ArrayList<>();
                    String attributeName = "";
                    for (String a : c.attributeNodes) {
                        if (attributeName.length() > 0) {
                            attributeName += " ";
                        }
                        attributeName += tree.labelledNodes.get(a).getLeafLemmas();
                    }
                    for (QueryModel qmt : res) {
                        QueryModel extQm = new QueryModel();
                        extQm.getConstraints().addAll(qmt.getConstraints());
                        extQm.getConstraints().add(new QueryConstraint(c.entityVariableName, "lookupAttribute(" + attributeName + ")", c.valueVariableName, c.optional));
                        extRes.add(extQm);
                    }
                    res = extRes;
                } else if (qc instanceof IOptionalCategoryQueryConstraint) {
                    IOptionalCategoryQueryConstraint c = (IOptionalCategoryQueryConstraint) qc;
                    if (res.isEmpty()) {
                        throw new Exception("Cannot extend conditions with category of " + c.entityVariableName + ". Current constraint set is empty");
                    }
                    ArrayList<QueryModel> extRes = new ArrayList<>();
                    for (QueryModel qmt : res) {
                        String evn = getNextEntityVariableName();
                        String cvn = getNextEntityVariableName();
                        for (QueryModel eqm : resolveEntityNode(tree.labelledNodes.get(c.getNodeLabel()), evn, false, true)) {
                            QueryModel extQm = new QueryModel();
                            extQm.getConstraints().addAll(qmt.getConstraints());
                            extQm.getConstraints().addAll(eqm.getConstraints());
                            extQm.getConstraints().add(new QueryConstraint(c.entityVariableName, "rdf:type", cvn, true));
                            extQm.getConstraints().add(new QueryConstraint(evn, "rdf:type", cvn, false));
                            extRes.add(extQm);
                        }
                    }
                    res = extRes;
                }
            }
        }
        return res;
    }

    ArrayList<QueryModel> resolveEntityNode(SyntacticTreeNode root, String entityVariableName, boolean includeSpecificEntity, boolean includeCategoryEntities) throws Exception {

        ArrayList<QueryModel> res = new ArrayList<>();
        if (root.npSimple || root.whnpSimple) {
            String entityName = root.getLeafValues();
            /*
             System.out.println("Finding entities named " + entityName);
             ArrayList<NamedEntityLookupResult> entityLookup = (ArrayList<NamedEntityLookupResult>) ontology.lookupEntity(entityName);
             for (NamedEntityLookupResult ner : entityLookup) {
             QueryModel qm = new QueryModel();
             qm.getConstraints().add(new QueryConstraint(entityVariableName, "is", ner.getNamedEntity().getUri(), false));
             res.add(qm);
             }
             */
            if (includeSpecificEntity) {
                QueryModel qm = new QueryModel();
                qm.getConstraints().add(new QueryConstraint(entityVariableName, "is", "lookupEntity(" + entityName + ")", false));
                res.add(qm);
            }
            /*
             System.out.println("Finding categories named " + categoryName);            
             ArrayList<CategoryLookupResult> categoryLookup = (ArrayList<CategoryLookupResult>) ontology.lookupCategory(categoryName);
             for (CategoryLookupResult cr : categoryLookup) {
             QueryModel qm = new QueryModel();
             qm.getConstraints().add(new QueryConstraint(entityVariableName, "rdf:type", cr.getCategory().getURI(), false));
             res.add(qm);
             }
             */
            if (includeCategoryEntities) {
                String categoryName = root.getLeafLemmas();
                QueryModel qm = new QueryModel();
                qm = new QueryModel();
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
        res.addAll(resolveValueNPPP(root, entityVariableName, valueVariableName));
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
    ArrayList<QueryModel> resolveValueNPPP(SyntacticTreeNode node, String entityVariableName, String valueVariableName) throws Exception {
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

    //receive a node (root) and constructs a set of contraints with the entity calles entityVariableName as subject of the constraints
    //using the PP and VP siblings of the node
    ArrayList<QueryModel> resolveSiblingConstraints(SyntacticTreeNode root, String entityVariableName) throws Exception {
        ArrayList<QueryModel> res = new ArrayList<>();
        for (SyntacticTreeNode s : root.parent.children) {
            if (s == root) {
                continue;
            }
            ArrayList<QueryModel> qms = new ArrayList<>();
            if (s.value.equals("PP")) {
                qms = ppCache.get(s);
                if (qms == null) {
                    qms = resolvePPconstraints(s, entityVariableName, "");
                    ppCache.put(s, qms);
                    ppCacheLabel.put(s, entityVariableName);
                } else {
                    qms = copyAndReplaceEntityVariableName(qms, ppCacheLabel.get(s), entityVariableName);
                }
            } else if (s.value.equals("VP")) {

            }
            if (res.isEmpty()) {
                res = qms;
            } else {
                ArrayList<QueryModel> newRes = new ArrayList<>();
                for (QueryModel qm1 : res) {
                    for (QueryModel qm2 : qms) {
                        QueryModel qm3 = new QueryModel();
                        qm3.getConstraints().addAll(qm1.getConstraints());
                        qm3.getConstraints().addAll(qm2.getConstraints());
                        newRes.add(qm3);
                    }
                }
                res = newRes;
            }
        }
        return res;
    }

    private String reduceGeneralExpression(String expr, SyntacticTree tree) {
        String[] exprs = expr.trim().split("\\+");
        String res = "";
        for (String s : exprs) {
            if (res.length() > 0) {
                res += " ";
            }
            res += reduceSingleExpression(s, tree);
        }
        return res;
    }

    private String reduceSingleExpression(String expr, SyntacticTree tree) {
        expr = expr.trim();
        if (expr.startsWith("w(")) {
            expr = expr.replaceFirst("w\\(", "");
            expr = expr.substring(0, expr.length() - 1);
            return tree.labelledNodes.get(expr).getLeafValues();
        } else if (expr.startsWith("l(")) {
            expr = expr.replaceFirst("l\\(", "");
            expr = expr.substring(0, expr.length() - 1);
            return tree.labelledNodes.get(expr).getLeafLemmas();
        }
        if (expr.startsWith("entities(")) {
            expr = expr.replaceFirst("entities\\(", "");
            expr = expr.substring(0, expr.length() - 1);
            return "entities(\n" + tree.labelledNodes.get(expr).toString() + "\n)";
        }
        if (expr.startsWith("values(")) {
            expr = expr.replaceFirst("values\\(", "");
            expr = expr.substring(0, expr.length() - 1);
            return "values(\n" + tree.labelledNodes.get(expr).toString() + "\n)";
        }
        return expr;
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

    private ArrayList<QueryModel> resolvePPconstraints(SyntacticTreeNode s, String entityVariableName, String attributePrefix) throws Exception {
        if (attributePrefix == null) {
            attributePrefix = "";
        } else {
            attributePrefix = attributePrefix.trim() + " ";
        }
        ArrayList<QueryModel> res = new ArrayList<>();
        //find the preposition
        SyntacticTreeNode inNode = null;
        for (SyntacticTreeNode c : s.children) {
            if (c.value.equals("IN")) {
                inNode = c;
                break;
            }
        }
        if (inNode == null) {
            System.out.println("PP node without IN child");
        } else {
            if (inNode.children.size() != 1) {
                System.out.println("IN node with " + inNode.children.size() + " children");
            } else {
                String prep = inNode.children.get(0).value;
                SyntacticTreeNode npNode = null;
                for (SyntacticTreeNode c : s.children) {
                    if (c.value.equals("NP")) {
                        npNode = c;
                        break;
                    }
                }
                if (npNode == null) {
                    System.out.println("PP node without NP child");
                } else {
                    if (prep.equalsIgnoreCase("of")) {
                        //String newEntityName1 = getNextEntityVariableName();
                        //QueryConstraint boundingContraint = new QueryConstraint(entityVariableName, "is", newEntityName1, false);
                        for (QueryModel qm2 : resolveEntityNode(npNode, entityVariableName, true, true)) {
                            QueryModel qm = new QueryModel();
                            //qm.getConstraints().add(boundingContraint);
                            qm.getConstraints().addAll(qm2.getConstraints());
                            res.add(qm);
                        }

                        String newEntityName2 = getNextEntityVariableName();
                        for (QueryModel qm2 : resolveValueNode(npNode, newEntityName2, entityVariableName, "")) {
                            QueryModel qm = new QueryModel();
                            //qm.getConstraints().add(boundingContraint);
                            qm.getConstraints().addAll(qm2.getConstraints());
                            res.add(qm);
                        }
                    } else {
                        String newEntityName1 = getNextEntityVariableName();
                        QueryConstraint boundingContraint = new QueryConstraint(entityVariableName, "lookupAttribute(" + attributePrefix + prep + ")", newEntityName1, false);
                        for (QueryModel qm2 : resolveEntityNode(npNode, newEntityName1, true, true)) {
                            QueryModel qm = new QueryModel();
                            qm.getConstraints().add(boundingContraint);
                            qm.getConstraints().addAll(qm2.getConstraints());
                            res.add(qm);
                        }
                        String newEntityName2 = getNextEntityVariableName();
                        for (QueryModel qm2 : resolveValueNode(npNode, entityVariableName, newEntityName2, attributePrefix + prep)) {
                            QueryModel qm = new QueryModel();
                            //qm.getConstraints().add(boundingContraint);
                            qm.getConstraints().addAll(qm2.getConstraints());
                            res.add(qm);
                        }

                        for (QueryModel qm3 : resolveValueNode(npNode, newEntityName2, newEntityName1, "")) {
                            QueryModel qm = new QueryModel();
                            qm.getConstraints().add(boundingContraint);
                            qm.getConstraints().addAll(qm3.getConstraints());
                            res.add(qm);
                        }

                    }
                }
            }
        }
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
