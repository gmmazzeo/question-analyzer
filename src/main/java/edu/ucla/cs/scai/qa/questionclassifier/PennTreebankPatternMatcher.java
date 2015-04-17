/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cs.scai.qa.questionclassifier;

import edu.ucla.cs.scai.swim.qa.ontology.QueryMapping;
import edu.ucla.cs.scai.swim.qa.ontology.dbpedia.DBpediaOntology;
import edu.ucla.cs.scai.swim.qa.ontology.QueryModel;
import edu.ucla.cs.scai.swim.qa.ontology.dbpedia.TagMeClient;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 *
 * @author Giuseppe M. Mazzeo <mazzeo@cs.ucla.edu>
 */
public class PennTreebankPatternMatcher {

    private static final HashMap<String, PennTreebankPattern> patterns = new HashMap<>();

    private Parser parser = new Parser(DBpediaOntology.getInstance());

    private static ArrayList<String> getResources(final Pattern pattern) {
        final ArrayList<String> retval = new ArrayList<>();
        final String classPath = System.getProperty("java.class.path", ".");
        final String[] classPathElements = classPath.split(File.pathSeparator);
        for (final String element : classPathElements) {
            retval.addAll(getResources(element, pattern));
        }
        return retval;
    }

    private static ArrayList<String> getResources(final String element, final Pattern pattern) {
        final ArrayList<String> retval = new ArrayList<>();
        final File file = new File(element);
        if (file.isDirectory()) {
            retval.addAll(getResourcesFromDirectory(file, pattern));
        }
        return retval;
    }

    private static ArrayList<String> getResourcesFromDirectory(final File directory, final Pattern pattern) {
        System.out.println("Loading patterns in "+directory.getAbsolutePath());
        final ArrayList<String> retval = new ArrayList<>();
        final File[] fileList = directory.listFiles();
        for (final File file : fileList) {
            if (file.isDirectory()) {
                retval.addAll(getResourcesFromDirectory(file, pattern));
            } else {
                try {
                    final String fileName = file.getCanonicalPath();
                    final boolean accept = pattern.matcher(fileName).matches();
                    if (accept) {
                        retval.add(fileName);
                    }
                } catch (final IOException e) {
                    throw new Error(e);
                }
            }
        }
        return retval;
    }

    public PennTreebankPatternMatcher() {
        this(getResources(Pattern.compile(".*\\.prn")));
    }
    
    public PennTreebankPatternMatcher(final String directoryName) {   
        this(new File(directoryName));
    }    
    
    public PennTreebankPatternMatcher(final File directory) {                
        this(getResourcesFromDirectory(directory, Pattern.compile(".*\\.prn")));
    }    
    
    public PennTreebankPatternMatcher(Collection<String> list) {        
        for (final String fileName : list) {
            System.out.println(fileName);
            try {
                BufferedReader in = new BufferedReader(new FileReader(fileName));
                String l = in.readLine();
                String s = "";
                while (l != null) {
                    if (s.length() > 0) {
                        s += "\n";
                    }
                    s += l;
                    l = in.readLine();
                }
                String[] fn = fileName.split(File.separator);
                fn = fn[fn.length - 1].split("\\.");
                PennTreebankPattern p = new PennTreebankPattern(fn[0], s);
                p.root.print(0);
                patterns.put(p.name, p);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }        

    public HashMap<PennTreebankPattern, SyntacticTree> match(SyntacticTree st) throws Exception {
        HashMap<PennTreebankPattern, SyntacticTree> res = new HashMap<>();
        for (PennTreebankPattern pattern : patterns.values()) {
            SyntacticTree t = new SyntacticTree(st);
            if (t.match(pattern)) {
                res.put(pattern, t);
            }
        }
        return res;
    }

    public static void main(String[] args) throws Exception {
        TagMeClient tm = new TagMeClient();
        BufferedReader in = new BufferedReader(new InputStreamReader(PennTreebankPatternMatcher.class.getResourceAsStream("/qald3")));
        String l = in.readLine();
        int tot = 0;
        int ok = 0;
        PennTreebankPatternMatcher matcher = new PennTreebankPatternMatcher();
        HashMap<String, ArrayList<String>> questions = new HashMap<>();
        long time0 = 0;
        long time1 = 0;
        long time2 = 0;
        long time3 = 0;
        int n = 0;
        QueryMapping qmap = new QueryMapping();
        while (l != null && l.length() > 0) {
            if (!l.startsWith("%")) {
                System.out.println("\n\n************************************************************************************************\n" + l);
//                for (DBpediaEntityAnnotationResult r:tm.getTagMeResult(l)) {
//                    System.out.println(r);
//                }
                tot++;
                try {
                    long start = System.currentTimeMillis();
                    SyntacticTree st = matcher.parser.parse(l);
                    long stop = System.currentTimeMillis();
                    time0 += stop - start;
                    start = System.currentTimeMillis();
                    HashMap<PennTreebankPattern, SyntacticTree> matches = matcher.match(st);
                    stop = System.currentTimeMillis();
                    time1 += stop - start;
                    n++;
                    for (PennTreebankPattern match : matches.keySet()) {
                        ArrayList<String> q = questions.get(match.name);
                        if (q == null) {
                            q = new ArrayList<>();
                            questions.put(match.name, q);
                        }
                        q.add(l);
                    }
                    if (!matches.isEmpty()) {
                        ok++;
                    } else {
                        ArrayList<String> q = questions.get("> No match");
                        if (q == null) {
                            q = new ArrayList<>();
                            questions.put("> No match", q);
                        }
                        q.add(l);
                    }

                    ArrayList<QueryModel> initialModels = new ArrayList<>();
                    for (PennTreebankPattern pattern : matches.keySet()) {
                        System.out.println(pattern.name);
                        start = System.currentTimeMillis();
                        QueryResolver2 qr = new QueryResolver2(matches.get(pattern));
                        initialModels.addAll(qr.resolveIQueryModels(pattern));
                        stop = System.currentTimeMillis();
                        time2 += stop - start;
                    }
                    start = System.currentTimeMillis();
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
                    stop = System.currentTimeMillis();
                    time2 += stop - start;
                    System.out.println();
                    for (QueryModel im : initialModels) {
                        System.out.println("Weight: " + im.getWeight());
                        System.out.println(im);
                        System.out.println("-------------------------");
                    }
                    start = System.currentTimeMillis();
                    qmap.mapOnOntology(initialModels, DBpediaOntology.getInstance());
                    stop = System.currentTimeMillis();
                    time3 += stop - start;
                    System.out.println("total parse time: " + time0 + " msec");
                    System.out.println("total pattern match: " + time1 + " msec");
                    System.out.println("total pattern resolve: " + time2 + " msec");
                    System.out.println("total mapping time: " + time3 + " msec");
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
            l = in.readLine();
        }
        for (String t : new TreeSet<>(questions.keySet())) {
            System.out.println("==========================================");
            System.out.println(t);
            System.out.println("------------------------------------------");
            for (String qs : questions.get(t)) {
                System.out.print(qs);
                for (String prn : new TreeSet<>(questions.keySet())) {
                    if (prn.equals(t)) {
                        continue;
                    }
                    for (String str : questions.get(prn)) {
                        if (str.equals(qs)) {
                            System.out.print(" <DUP>");
                            break;
                        }
                    }
                }
                System.out.println();
            }
        }
        System.out.println(ok + "/" + tot);
        System.out.println("total parse time: " + time0 + " msec");
        System.out.println("total pattern match: " + time1 + " msec");
        System.out.println("total pattern resolve: " + time2 + " msec");
        System.out.println("total mapping time: " + time3 + " msec");
        System.out.println("avg parse time: " + time0 / tot + " msec");
        System.out.println("avg pattern match: " + time1 / tot + " msec");
        System.out.println("avg pattern resolve: " + time2 / tot + " msec");

    }
}
