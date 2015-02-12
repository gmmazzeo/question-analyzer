/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cs.scai.qa.questionclassifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

/**
 *
 * @author Giuseppe M. Mazzeo <mazzeo@cs.ucla.edu>
 */
public class PennTreebankPatternNode {

    HashSet<String> values = new HashSet<>();
    HashSet<String> lemmas = new HashSet<>();
    HashSet<String> ners = new HashSet<>();
    String label;
    ArrayList<PennTreebankPatternNode> children = new ArrayList<>();

    PennTreebankPatternNode(String[] tokens, int[] currentPosition) throws Exception {
        if (!tokens[currentPosition[0]].equals("(")) {
            throw new Exception("Wrong pattern - (sub)tree does not start with (");
        }
        currentPosition[0]++;
        String[] s = tokens[currentPosition[0]].split("#");
        String[] elements = s[0].split("\\/");
        currentPosition[0]++;
        //System.out.print(elements[0]);
        values.addAll(Arrays.asList(elements[0].split("\\|")));
        if (elements.length > 1) {
            //System.out.print("/" + elements[1]);
            lemmas.addAll(Arrays.asList(elements[1].split("\\|")));
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
        while (tokens[currentPosition[0]].equals("(")) {
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
        print(values);
        if (lemmas.size() > 0) {
            System.out.print("/");
            print(lemmas);
            if (ners.size() > 0) {
                System.out.print("/");
                print(ners);
            }
        }
        if (label!=null) {
            System.out.print("#"+label);
        }
        System.out.println();
        for (PennTreebankPatternNode c : children) {
            c.print(l + 1);
        }
    }
    
    private void print(HashSet<String> a) {
        String delimiter="";
        for (String s:a) {
            System.out.print(delimiter+s);
            delimiter="|";
        }
    }
}
