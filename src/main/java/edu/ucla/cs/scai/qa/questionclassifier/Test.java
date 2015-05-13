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
                        System.out.println(ps.get(p));
                        QueryResolver2 qr = new QueryResolver2(ps.get(p));
                        initialModels.addAll(qr.resolveIQueryModels(p));
                    }

                    Collections.sort(initialModels);
                    double threshold = 0.1;
                    double maxWeight = initialModels.size() < 2 ? 1 : initialModels.get(0).getWeight();
                    for (QueryModel qm : initialModels) {
                        qm.setWeight(qm.getWeight() / maxWeight);
                    }
                    for (int i = 0 ; i < initialModels.size(); i++) {
                        if (initialModels.get(i).getWeight() < threshold) {
                            initialModels.remove(i);
                            i--;
                        }
                    }
                    System.out.println("Final query models with weight above threshold: " + initialModels.size());
                    System.out.println("#####################################");
                    System.out.println("######### INITIAL MODELS ############");
                    System.out.println("#####################################");
                    int num = 0;
                    for (QueryModel im : initialModels) {
                        im.setModelNumber(num++);
                        System.out.println("Weight: " + im.getWeight());
                        System.out.println("Number: " + im.getModelNumber());
                        System.out.println(im);
                        System.out.println("-------------------------");
                    }
                    System.out.println();

                    QueryMapping qm = new QueryMapping();
                    ArrayList<QueryModel> mappedModels = qm.mapOnOntology(initialModels, DBpediaOntology.getInstance());
                    System.out.println("#####################################");
                    System.out.println("######### MAPPED MODELS #############");
                    System.out.println("#####################################");
                    for (QueryModel mappedModel : mappedModels) {
                        System.out.println("Weight: " + mappedModel.getWeight());
                        System.out.println("Number: " + mappedModel.getModelNumber());
                        System.out.println(mappedModel);
                        System.out.println("-------------------------");
                    }

                    System.out.println("#####################################");
                    System.out.println("######### SPARQL RESULT #############");
                    System.out.println("#####################################");
                    String answer = "Answer not found";
                    QueryModel resultModel = null;
                    DBpediaOntology ontology = DBpediaOntology.getInstance();
                    for (QueryModel m : mappedModels) {
                        ArrayList<HashMap<String, String>> res = ontology.executeSparql(m, 20);
                        if (!res.isEmpty()) {
                            resultModel = m;
                            if (m.getExampleEntity() == null) {
                                m.setExampleEntity(res.get(0).get(m.getEntityVariableName()));
                            }
                            answer = "";
                            for (HashMap<String, String> pairs : res) {
                                for (String v : pairs.values()) {
                                    answer += v + "\t";
                                }
                                answer += "\n";
                            }
                            break;
                        }
                    }
                    System.out.println("answer:\n" + answer);
                    if (resultModel != null) {
                        System.out.println("Answer found with model " + resultModel.getModelNumber() + "\n" + resultModel);
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
