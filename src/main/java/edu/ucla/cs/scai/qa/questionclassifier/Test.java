/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cs.scai.qa.questionclassifier;

import edu.ucla.cs.scai.swim.qa.ontology.QueryMapping;
import edu.ucla.cs.scai.swim.qa.ontology.QueryModel;
import edu.ucla.cs.scai.swim.qa.ontology.dbpedia.DBpediaOntology;
import edu.ucla.cs.scai.swim.qa.ontology.dbpedia.DBpediaOntologyWithStatistics;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

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
        Parser parser = new Parser(DBpediaOntologyWithStatistics.getInstance());
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
                    HashMap<PennTreebankPattern, SyntacticTree> ps = matcher.match(qt);
                    ArrayList<QueryModel> initialModels = new ArrayList<>();
                    for (PennTreebankPattern p : ps.keySet()) {
                        System.out.println("Pattern found: " + p.name);
                        QueryResolver2 qr = new QueryResolver2(ps.get(p));
                        initialModels.addAll(qr.resolveIQueryModels(p));
                    }

                    Collections.sort(initialModels);
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
                    ArrayList<QueryModel> mappedModels = qm.mapOnOntology(initialModels, DBpediaOntologyWithStatistics.getInstance());
                    System.out.println("#####################################");
                    System.out.println("######### MAPPED MODELS #############");
                    System.out.println("#####################################");
                    for (int i = 0; i < Math.min(initialModels.size(), mappedModels.size()); i++) {
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
