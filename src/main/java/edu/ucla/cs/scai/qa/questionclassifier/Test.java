/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cs.scai.qa.questionclassifier;

import edu.ucla.cs.scai.swim.qa.ontology.QueryMapping;
import edu.ucla.cs.scai.swim.qa.ontology.QueryModel;
import edu.ucla.cs.scai.swim.qa.ontology.dbpedia.DBpediaOntology;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;

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
        Parser parser = new Parser(DBpediaOntology.getInstance());
        PennTreebankPatternMatcher matcher = new PennTreebankPatternMatcher();
        while (true) {
            System.out.print("question> ");
            String qt = in.readLine();
            if (qt.equals("exit")) {
                break;
            } else if (qt.equals("reload")) {
                matcher = new PennTreebankPatternMatcher();
            } else {
                try {
                    SyntacticTree t = parser.parse(qt);
                    System.out.println(t.toString());
                    HashMap<PennTreebankPattern, SyntacticTree> ps = matcher.match(t);
                    ArrayList<QueryModel> initialModels = new ArrayList<>();
                    for (PennTreebankPattern p : ps.keySet()) {
                        System.out.println("Pattern found: " + p.name);
                        QueryResolver2 qr = new QueryResolver2(ps.get(p));
                        initialModels.addAll(qr.resolveIQueryModels(p));
                    }

                    Collections.sort(initialModels);
                    double threshold = 0.1;
                    double maxWeight = initialModels.isEmpty() ? 0 : initialModels.get(0).getWeight();
                    for (Iterator<QueryModel> it = initialModels.iterator(); it.hasNext();) {
                        QueryModel im = it.next();
                        im.setWeight(im.getWeight() / maxWeight);
                        if (im.getWeight() < threshold) {
                            it.remove();
                        }
                    }
                    System.out.println("Final query models with weight above threshold : " + initialModels.size());
                    System.out.println("#####################################");
                    System.out.println("######### INITIAL MODELS ############");
                    System.out.println("#####################################");
                    for (QueryModel im : initialModels) {
                        System.out.println("Weight: " + im.getWeight());
                        System.out.println(im);
                        System.out.println("-------------------------");
                    }
                    System.out.println();
                    QueryMapping qm = new QueryMapping();
                    ArrayList<QueryModel> mappedModels = qm.mapOnOntology(initialModels, DBpediaOntology.getInstance());
                    System.out.println("#####################################");
                    System.out.println("######### MAPPED MODELS #############");
                    System.out.println("#####################################");
                    for (int i = 0; i < mappedModels.size(); i++) {
                        System.out.println("Weight: " + mappedModels.get(i).getWeight());
                        System.out.println(mappedModels.get(i));
                        System.out.println("-------------------------");
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    //SyntacticTree t = parser.parse(qt);
                    //t.compactNamedEntities();
                    //System.out.println(t);
                }
            }
        }
    }

}
