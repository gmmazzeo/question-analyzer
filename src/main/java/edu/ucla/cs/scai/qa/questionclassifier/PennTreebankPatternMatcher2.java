/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cs.scai.qa.questionclassifier;

import edu.stanford.nlp.ling.CoreLabel;
import edu.ucla.cs.scai.swim.qa.ontology.Ontology;
import edu.ucla.cs.scai.swim.qa.ontology.dbpedia.DBpediaOntology;
import edu.ucla.cs.scai.swim.qa.ontology.QueryConstraint;
import edu.ucla.cs.scai.swim.qa.ontology.QueryModel;
import edu.ucla.cs.scai.swim.qa.ontology.dbpedia.DBpediaEntityAnnotationResult;
import edu.ucla.cs.scai.swim.qa.ontology.dbpedia.TagMeClient;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 *
 * @author Giuseppe M. Mazzeo <mazzeo@cs.ucla.edu>
 */
public class PennTreebankPatternMatcher2 {

    private static final HashMap<String, PennTreebankPattern> patterns = new HashMap<>();

    private Parser parser = new Parser(DBpediaOntology.getInstance());

    private static ArrayList<String> getResources(
            final Pattern pattern) {
        final ArrayList<String> retval = new ArrayList<>();
        final String classPath = System.getProperty("java.class.path", ".");
        final String[] classPathElements = classPath.split(File.pathSeparator);
        for (final String element : classPathElements) {
            retval.addAll(getResources(element, pattern));
        }
        return retval;
    }

    private static ArrayList<String> getResources(
            final String element,
            final Pattern pattern) {
        final ArrayList<String> retval = new ArrayList<>();
        final File file = new File(element);
        if (file.isDirectory()) {
            retval.addAll(getResourcesFromDirectory(file, pattern));
        }
        return retval;
    }

    private static ArrayList<String> getResourcesFromDirectory(
            final File directory,
            final Pattern pattern) {
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

    private int countLevels(PennTreebankPatternNode node) {
        if (node.children.isEmpty()) {
            return 1;
        } else {
            int max = 0;
            for (PennTreebankPatternNode c : node.children) {
                max = Math.max(max, countLevels(c));
            }
            return max + 1;
        }
    }

    public PennTreebankPatternMatcher2() {
        Pattern pattern = Pattern.compile(".*\\.prn");
        final Collection<String> list = getResources(pattern);
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
                System.out.println(countLevels(p.root));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public HashMap<PennTreebankPattern, SyntacticTree> match(String s) throws Exception {
        SyntacticTree st = parser.parse(s);
        HashMap<PennTreebankPattern, SyntacticTree> res = new HashMap<>();
        for (PennTreebankPattern pattern : patterns.values()) {
            if (st.match(pattern)) {
                res.put(pattern, st);
            }
        }
        return res;
    }

    public static void main(String[] args) throws Exception {
        PennTreebankPatternMatcher2 matcher = new PennTreebankPatternMatcher2();
    }
}
