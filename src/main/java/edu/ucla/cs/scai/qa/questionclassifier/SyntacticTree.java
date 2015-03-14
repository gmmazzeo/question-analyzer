package edu.ucla.cs.scai.qa.questionclassifier;

import edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;
import edu.ucla.cs.scai.swim.qa.ontology.NamedEntityAnnotationResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Giuseppe M. Mazzeo <mazzeo@cs.ucla.edu>
 */
public class SyntacticTree {

    SyntacticTreeNode root;

    ArrayList<CoreLabel> tokens;

    QuestionFocus focus;
    SyntacticTreeNode examplePage;

    HashMap<String, SyntacticTreeNode> labelledNodes = new HashMap<>();

    String focusPossibilities; //c=category, a=attribute, e=entity

    ArrayList<NamedEntityAnnotationResult> namedEntityAnnotations;

    public SyntacticTree(CoreMap sentenceTree, CoreMap sentenceTokens) throws Exception {
        Tree tree = sentenceTree.get(TreeAnnotation.class);
        List<Tree> leaves = tree.getLeaves();
        tokens = (ArrayList<CoreLabel>) sentenceTokens.get(TokensAnnotation.class);
        Iterator<CoreLabel> it2 = tokens.iterator();
        Iterator<Tree> it1 = leaves.iterator();
        HashMap<Integer, CoreLabel> map = new HashMap<>();
        while (it1.hasNext() && it2.hasNext()) {
            map.put(it1.next().nodeNumber(tree), it2.next());
        }
        if (it1.hasNext() || it2.hasNext()) {
            throw new Exception("Different number of leaves and tokens!");
        }
        root = new SyntacticTreeNode(tree, map, null, tree);
    }

    public void compactNamedEntities() {
        root.compactNamedEntities();
    }

    @Override
    public String toString() {
        return root.toString();
    }

    public boolean match(PennTreebankPattern pattern) {
        HashMap<SyntacticTreeNode, PennTreebankPatternNode> pairs = new HashMap<>();
        if (root.match(pattern.root, pairs)) {
            for (SyntacticTreeNode k : pairs.keySet()) {
                PennTreebankPatternNode v = pairs.get(k);
                if (v.label != null) {
                    labelledNodes.put(v.label, k);
                    //System.out.println(k.value + " -> " + v.label);
                }
            }
            return true;
        } else {
            return false;
        }
    }

    public void setNamedEntityAnnotations(ArrayList<NamedEntityAnnotationResult> namedEntityAnnotations) {
        this.namedEntityAnnotations = namedEntityAnnotations;
    }

    public ArrayList<NamedEntityAnnotationResult> getNamedEntityAnnotations() {
        return namedEntityAnnotations;
    }
}
