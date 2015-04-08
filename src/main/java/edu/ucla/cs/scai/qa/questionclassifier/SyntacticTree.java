package edu.ucla.cs.scai.qa.questionclassifier;

import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;
import edu.ucla.cs.scai.swim.qa.ontology.NamedEntityAnnotationResult;
import java.util.ArrayList;
import java.util.HashMap;
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
    String focusPossibilities; //c = category, a = attribute, e = entity
    ArrayList<NamedEntityAnnotationResult> namedEntityAnnotations;

    public SyntacticTree(CoreMap sentenceTree, CoreMap sentenceTokens, ArrayList<NamedEntityAnnotationResult> namedEntityAnnotations) throws Exception {
        Tree tree = sentenceTree.get(TreeAnnotation.class);
        List<Tree> leaves = tree.getLeaves();
        tokens = (ArrayList<CoreLabel>) sentenceTokens.get(TokensAnnotation.class);
        if (leaves.size() != tokens.size()) {
            throw new Exception("Different number of leaves and tokens!");
        }
        root = new SyntacticTreeNode(tree, tokens, null);
        this.namedEntityAnnotations = namedEntityAnnotations;
    }

    public SyntacticTree(SyntacticTree st) throws Exception {
        root = st.root;
        tokens = st.tokens;
        focus = st.focus;
        examplePage = st.examplePage;
        focusPossibilities = st.focusPossibilities;
        namedEntityAnnotations = st.namedEntityAnnotations;
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
                    labelledNodes.put(pattern.name+"#"+v.label, k);
                    //System.out.println(k.value + " -> " + v.label);
                }
            }
            return true;
        } else {
            return false;
        }
    }

    public ArrayList<NamedEntityAnnotationResult> getNamedEntityAnnotations() {
        return namedEntityAnnotations;
    }
}
