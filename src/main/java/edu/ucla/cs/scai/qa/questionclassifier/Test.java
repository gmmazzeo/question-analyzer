/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cs.scai.qa.questionclassifier;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.ucla.cs.scai.swim.qa.ontology.QueryModel;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

/**
 *
 * @author Giuseppe M. Mazzeo <mazzeo@cs.ucla.edu>
 */
public class Test {

    //give a look to these pages:
    //http://www.surdeanu.info/mihai/teaching/ista555-fall13/readings/PennTreebankConstituents.html
    //http://nlp.stanford.edu/software/dependencies_manual.pdf
    //They explain the tags used by Stanford Parser
    public static void main(String args[]) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        Parser parser = new Parser();
        PennTreebankPatternMatcher m = new PennTreebankPatternMatcher();
        while (true) {
            System.out.print("question> ");
            String qt = in.readLine();
            if (qt.equals("exit")) {
                break;
            } else if(qt.equals("reload")) {
                m = new PennTreebankPatternMatcher();
            } else {
                try {
                    SyntacticTree t = parser.parse(qt);
                    System.out.println(t.toString());
                    HashMap<PennTreebankPattern, SyntacticTree> ps = m.match(qt);
                    for (PennTreebankPattern p : ps.keySet()) {
                        System.out.println("Pattern found: " + p.name);
                        QueryResolver qr = new QueryResolver(ps.get(p));
                        for (QueryModel qm : qr.resolveIQueryModels(p)) {
                            System.out.println();
                            System.out.println(qm);
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    SyntacticTree t = parser.parse(qt);
                    t.compactNamedEntities();
                    System.out.println(t);
                }
            }
        }
    }

}
