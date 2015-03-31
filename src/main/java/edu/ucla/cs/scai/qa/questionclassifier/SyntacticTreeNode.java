package edu.ucla.cs.scai.qa.questionclassifier;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.IntPair;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Giuseppe M. Mazzeo <mazzeo@cs.ucla.edu>
 */
public class SyntacticTreeNode implements Externalizable {

    String value, lemma, ner, identifier;

    SyntacticTreeNode parent;
    boolean containsNamedEntities;
    boolean isFocus;
    int categoryPriority, attributePriority, entityPriority;
    boolean examplePage;
    boolean npSimple, npCompound, whnpSimple, whnpCompound, npQp;
    int begin, end;
    boolean isLeafParent;

    ArrayList<SyntacticTreeNode> children = new ArrayList<>();

    private static final HashSet<String> standardTagsForLookup = new HashSet<>();

    static {
        standardTagsForLookup.add("NP");
        standardTagsForLookup.add("NN");
        standardTagsForLookup.add("NNS");
        standardTagsForLookup.add("NNP");
        standardTagsForLookup.add("NNPS");
        standardTagsForLookup.add("WHNP");
        standardTagsForLookup.add("IN");
        standardTagsForLookup.add("TO");
        standardTagsForLookup.add("VP");
        standardTagsForLookup.add("VB");
        standardTagsForLookup.add("VBD");
        standardTagsForLookup.add("VBG");
        standardTagsForLookup.add("VBN");
        standardTagsForLookup.add("VBP");
        standardTagsForLookup.add("VBZ");
        standardTagsForLookup.add("CD");
        standardTagsForLookup.add("CC");
        standardTagsForLookup.add("JJ");
        standardTagsForLookup.add("RB");
    }

    public SyntacticTreeNode(Tree t, ArrayList<CoreLabel> tokens, SyntacticTreeNode parent) throws Exception {
        this.parent = parent;
        value = t.value();
        if (t.isLeaf()) {
            CoreLabel c = tokens.remove(0);
            begin = c.beginPosition();
            end = c.endPosition();
            parent.isLeafParent = true;
            if (c == null) {
                throw new Exception("Mapping between TreeNode and CoreLabel not found");
            } else {
                lemma = c.lemma();
                ner = c.ner();
                //System.out.println(value + " -> " + c.value());
                if (!value.equals(c.value())) {
                    throw new Exception("Different words have been matched!");
                }
            }
        } else {
            boolean hasNPchildren = false;
            boolean hasWHNPchildren = false;
            boolean hasQPchildren = false;
            begin = Integer.MAX_VALUE;
            end = Integer.MIN_VALUE;
            for (Tree c : t.children()) {
                SyntacticTreeNode child = new SyntacticTreeNode(c, tokens, this);
                children.add(child);
                if (child.value.equals("NP")) {
                    hasNPchildren = true;
                } else if (child.value.equals("QP")) {
                    hasQPchildren = true;
                } else if (child.value.equals("WHNP")) {
                    hasWHNPchildren = true;
                }
                begin = Math.min(begin, child.begin);
                end = Math.max(end, child.end);
            }
            if (value.equals("NP")) {
                if (hasNPchildren) {
                    npCompound = true;
                } else if (hasQPchildren) {
                    npQp = true;
                } else {
                    npSimple = true;
                }
            } else if (value.equals("WHNP")) { //can a WHNP node have QP children?
                if (hasNPchildren || hasWHNPchildren) {
                    whnpCompound = true;
                } else if (!hasQPchildren) {
                    whnpSimple = true;
                }
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        fillStringBuilder(sb, 0);
        return sb.toString();
    }

    private void fillStringBuilder(StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) {
            sb.append("\t");
        }
        sb.append(value).append("\\").append(lemma).append("\\").append(ner);
        if (identifier != null) {
            sb.append("#" + identifier);
        }
        sb.append(" [").append(begin).append("..").append(end).append("]");
        for (SyntacticTreeNode c : children) {
            sb.append("\n");
            c.fillStringBuilder(sb, level + 1);
        }
    }

    public void compactNamedEntities() {
        if (true) {
            return;
        }
        //if (!ner.equals("O")) {
        //System.out.println(this);
        ArrayList<SyntacticTreeNode> newChildren = new ArrayList<>();
        String lemmaPrefix = "", wordPrefix = "", valuePrefix = "";
        for (Iterator<SyntacticTreeNode> it = children.iterator(); it.hasNext();) {
            SyntacticTreeNode c = it.next();
            //if ((!c.dependency.equals("prep") /*|| c.specific.equals("of")*/) && c.ner.equals(ner) && (c.tag.equals(tag) || tag.equals(c.tag + "S") || c.tag.equals(tag + "S"))) {
            if ((!c.ner.equals("O") && c.ner.equals(ner))) {
                it.remove();
                if (lemmaPrefix.length() > 0) {
                    lemmaPrefix += " ";
                }
                lemmaPrefix += c.lemma;
                if (wordPrefix.length() > 0) {
                    wordPrefix += " ";
                }
                wordPrefix += c.value;
                if (valuePrefix.length() > 0) {
                    valuePrefix += " ";
                }
                valuePrefix += c.value;
                newChildren.addAll(c.children);
            }
        }
        if (lemmaPrefix.length() > 0) {
            lemmaPrefix += " ";
        }
        lemma = lemmaPrefix + lemma;
        if (wordPrefix.length() > 0) {
            wordPrefix += " ";
        }
        value = wordPrefix + value;
        if (valuePrefix.length() > 0) {
            valuePrefix += " ";
        }
        value = valuePrefix + value;
        children.addAll(newChildren);
        //}
        for (SyntacticTreeNode c : children) {
            c.compactNamedEntities();
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(value);
        out.writeObject(lemma);
        out.writeObject(ner);
        out.writeObject(identifier);
        out.writeInt(children.size());
        for (SyntacticTreeNode c : children) {
            out.writeObject(c);
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        value = (String) in.readObject();
        lemma = (String) in.readObject();
        ner = (String) in.readObject();
        identifier = (String) in.readObject();
        int n = in.readInt();
        children = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            SyntacticTreeNode c = (SyntacticTreeNode) in.readObject();
            children.add(c);
            c.parent = this;
        }
    }

    boolean match(PennTreebankPatternNode patternNode, HashMap<SyntacticTreeNode, PennTreebankPatternNode> pairs) {
        if (patternNode.notValues.contains(value) || patternNode.notLemmas.contains(lemma)) {
            return false;
        }
        boolean ok
                = (patternNode.values.contains("*") || patternNode.values.contains(value))
                && (patternNode.lemmas.isEmpty() || patternNode.lemmas.contains("*") || patternNode.lemmas.contains(lemma))
                && (patternNode.ners.isEmpty() || patternNode.ners.contains("*") || patternNode.ners.contains(lemma));

        if (ok == false) {
            return false;
        }

        pairs.put(this, patternNode);

        LinkedList<PennTreebankPatternNode> nodesToBeMatched = new LinkedList();
        LinkedList<PennTreebankPatternNode> nodesNotToBeMatched = new LinkedList();
        for (PennTreebankPatternNode c : patternNode.children) {
            if (c.not) {
                nodesNotToBeMatched.add(c);
            } else {
                nodesToBeMatched.add(c);
            }
        }
        LinkedList<SyntacticTreeNode> nodesAvailable = new LinkedList(children);
        while (!nodesToBeMatched.isEmpty()) {
            PennTreebankPatternNode sn = nodesToBeMatched.removeFirst();
            boolean found = false;
            for (Iterator<SyntacticTreeNode> it = nodesAvailable.iterator(); it.hasNext();) {
                SyntacticTreeNode n = it.next();
                if (n.match(sn, pairs)) {
                    found = true;
                    it.remove();
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        while (!nodesNotToBeMatched.isEmpty()) {
            PennTreebankPatternNode sn = nodesNotToBeMatched.removeFirst();
            boolean found = false;
            for (SyntacticTreeNode n : nodesAvailable) {
                if (n.match(sn, pairs)) {
                    found = true;
                    break;
                }
            }
            if (found) {
                return false;
            }
        }
        return true;
    }

    /*
     QuestionFocus getFocus() {
     if (isFocus) {
     return new QuestionFocus(this, categoryPriority, attributePriority, entityPriority);
     }
     for (SyntacticTreeNode c : children) {
     QuestionFocus f = c.getFocus();
     if (f != null) {
     return f;
     }
     }
     return null;
     }
     */

    /*
     SyntacticTreeNode getExamplePage() {
     if (examplePage) {
     return this;
     }
     for (SyntacticTreeNode c : children) {
     SyntacticTreeNode e = c.getExamplePage();
     if (e != null) {
     return e;
     }
     }
     return null;
     }
     */
    public String getLeafValues(HashSet<String> tags) {
        StringBuilder sb = new StringBuilder();
        fillLeafValues(sb, tags);
        return sb.toString();
    }

    public String getLeafValues() {
        return getLeafValues(standardTagsForLookup);
    }

    public void fillLeafValues(StringBuilder sb, HashSet<String> tags) {
        if (children.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(value);
        } else {
            if (tags.contains(value)) {
                for (SyntacticTreeNode c : children) {
                    c.fillLeafValues(sb, tags);
                }
            }
        }
    }

    public String getLeafLemmas(HashSet<String> tags) {
        StringBuilder sb = new StringBuilder();
        fillLeafLemmas(sb, tags);
        return sb.toString();
    }

    public String getLeafLemmas() {
        return getLeafLemmas(standardTagsForLookup);
    }

    public void fillLeafLemmas(StringBuilder sb, HashSet<String> tags) {
        if (children.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(lemma);
        } else {
            if (tags.contains(value)) {
                for (SyntacticTreeNode c : children) {
                    c.fillLeafLemmas(sb, tags);
                }
            }
        }
    }

    public ArrayList<SyntacticTreeNode> getLeafParents() {
        ArrayList<SyntacticTreeNode> res = new ArrayList<>();
        fillLeafParentsArray(res);
        return res;
    }

    private void fillLeafParentsArray(ArrayList<SyntacticTreeNode> a) {
        if (isLeafParent) {
            a.add(this);
        } else {
            for (SyntacticTreeNode c:children) {
                c.fillLeafParentsArray(a);
            }
        }
    }
}
