/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cs.scai.qa.questionclassifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

/**
 *
 * @author Giuseppe M. Mazzeo <mazzeo@cs.ucla.edu>
 */
public class PennTreebankPatternNode {

    HashSet<String> values = new HashSet<>();
    HashSet<String> lemmas = new HashSet<>();
    HashSet<String> notValues = new HashSet<>();
    HashSet<String> notLemmas = new HashSet<>();
    HashSet<String> ners = new HashSet<>();
    String label;
    HashMap<String, String> labels = new HashMap<>();
    ArrayList<PennTreebankPatternNode> children = new ArrayList<>();
    boolean not;
    HashSet<String> leafOfPatterns = new HashSet<>(); //contains the names of the patterns for which this node is a leaf

    PennTreebankPatternNode(String[] tokens, int[] currentPosition) throws Exception {
        if (!tokens[currentPosition[0]].equals("(") && !tokens[currentPosition[0]].equals("^")) {
            throw new Exception("Wrong pattern - (sub)tree does not start with ( or ^");
        }
        if (tokens[currentPosition[0]].equals("^")) {
            not = true;
            currentPosition[0]++;
            if (!tokens[currentPosition[0]].equals("(")) {
                throw new Exception("Wrong pattern - ( required after ^");
            }
        }
        currentPosition[0]++; //skip (
        String[] s = tokens[currentPosition[0]].split("#");
        String[] elements = s[0].split("\\/");
        currentPosition[0]++;
        //System.out.print(elements[0]);
        for (String v : elements[0].split("\\|")) {
            if (v.startsWith("!")) {
                notValues.add(v.replace("!", ""));
            } else {
                values.add(v);
            }
        }
        if (elements.length > 1) {
            //System.out.print("/" + elements[1]);
            for (String l : elements[1].split("\\|")) {
                if (l.startsWith("!")) {
                    notLemmas.add(l.replace("!", "").toLowerCase());
                } else {
                    lemmas.add(l.toLowerCase());
                }
            }
            if (elements.length > 2) {
                //System.out.print("/" + elements[2]);
                ners.addAll(Arrays.asList(elements[2].split("\\|")));
            }
        }
        if (s.length > 1) {
            label = s[1];
            //System.out.print("#" + label);
        }
        //System.out.println();
        while (tokens[currentPosition[0]].equals("(") || tokens[currentPosition[0]].equals("^")) {
            children.add(new PennTreebankPatternNode(tokens, currentPosition));
        }
        if (!tokens[currentPosition[0]].equals(")")) {
            throw new Exception("Wrong pattern - (sub)tree does not end with )");
        }
        currentPosition[0]++;
    }

    public void print(int l) {
        for (int i = 0; i < l; i++) {
            System.out.print("\t");
        }
        if (not) {
            System.out.print("^");
        }
        print(values);
        if (notValues.size() > 0) {
            System.out.print("!(");
            print(notValues);
            System.out.print(")");
        }
        if (lemmas.size() > 0 || notLemmas.size() > 0) {
            System.out.print("/");
        }
        if (lemmas.size() > 0) {
            print(lemmas);
        }
        if (notValues.size() > 0) {
            System.out.print("!(");
            print(notValues);
            System.out.print(")");
        }
        if (ners.size() > 0) {
            System.out.print("/");
            print(ners);
        }
        if (label != null) {
            System.out.print("#" + label);
        }
        System.out.println();
        for (PennTreebankPatternNode c : children) {
            c.print(l + 1);
        }
    }

    private void print(HashSet<String> a) {
        String delimiter = "";
        for (String s : a) {
            System.out.print(delimiter + s);
            delimiter = "|";
        }
    }

    public boolean sameAs(PennTreebankPatternNode n) {
        return sameValues(n) && sameLemmas(n) && sameNotValues(n) && sameNotLemmas(n) && sameNers(n) && not == n.not;
    }

    public boolean sameValues(PennTreebankPatternNode n) {
        HashSet<String> vs = new HashSet<>(n.values);
        vs.removeAll(values);
        return vs.isEmpty();
    }

    public boolean sameLemmas(PennTreebankPatternNode n) {
        HashSet<String> ls = new HashSet<>(n.lemmas);
        ls.removeAll(lemmas);
        return ls.isEmpty();
    }

    public boolean sameNotValues(PennTreebankPatternNode n) {
        HashSet<String> vs = new HashSet<>(n.notValues);
        vs.removeAll(notValues);
        return vs.isEmpty();
    }

    public boolean sameNotLemmas(PennTreebankPatternNode n) {
        HashSet<String> ls = new HashSet<>(n.notLemmas);
        ls.removeAll(notLemmas);
        return ls.isEmpty();
    }

    public boolean sameNers(PennTreebankPatternNode n) {
        HashSet<String> ns = new HashSet<>(n.ners);
        ns.removeAll(ners);
        return ns.isEmpty();
    }

    public int annotateLeaves(String name) {
        if (children.isEmpty()) {
            leafOfPatterns.add(name);
            return 1;
        } else {
            int res = 0;
            for (PennTreebankPatternNode c : children) {
                res += c.annotateLeaves(name);
            }
            return res;
        }
    }

    public int countNodes() {
        int res = 1;
        for (PennTreebankPatternNode c:children) {
            res += c.countNodes();
        }
        return res;
    }

    public void fillLabels(String name) {
        if (label != null) {
            labels.put(name, label);
        }
        for (PennTreebankPatternNode c : children) {
            c.fillLabels(name);
        }
    }
}
