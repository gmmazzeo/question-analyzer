/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cs.scai.qa.questionclassifier;

import edu.ucla.cs.scai.swim.qa.ontology.dbpedia.DBpediaOntology;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Pattern;

/**
 *
 * @author Giuseppe M. Mazzeo <mazzeo@cs.ucla.edu>
 */
public class PennTreebankPatternMatcher2 {

    private final HashMap<String, Integer> numberOfLeaves = new HashMap<>();

    private PennTreebankPatternNode rootOfMergedPatterns;

    private static final HashMap<String, PennTreebankPattern> patterns = new HashMap<>();

    private Parser parser = new Parser(DBpediaOntology.getInstance());

    private ArrayList<String> getResources(
            final Pattern pattern) {
        final ArrayList<String> retval = new ArrayList<>();
        final String classPath = System.getProperty("java.class.path", ".");
        final String[] classPathElements = classPath.split(File.pathSeparator);
        for (final String element : classPathElements) {
            retval.addAll(getResources(element, pattern));
        }
        return retval;
    }

    private ArrayList<String> getResources(
            final String element,
            final Pattern pattern) {
        final ArrayList<String> retval = new ArrayList<>();
        final File file = new File(element);
        if (file.isDirectory()) {
            retval.addAll(getResourcesFromDirectory(file, pattern));
        }
        return retval;
    }

    private ArrayList<String> getResourcesFromDirectory(
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

    //add the children of n to the children on m, avoiding duplicates
    //it is assumed that m and n match
    private void mergePatterns(PennTreebankPatternNode m, PennTreebankPatternNode n) {
        ArrayList<PennTreebankPatternNode> availableNodes = new ArrayList<>(m.children);
        //finds existing children
        for (Iterator<PennTreebankPatternNode> it1 = n.children.iterator(); it1.hasNext();) {
            PennTreebankPatternNode cn = it1.next();
            for (Iterator<PennTreebankPatternNode> it2 = availableNodes.iterator(); it2.hasNext();) {
                PennTreebankPatternNode cm = it2.next();
                if (cn.sameAs(cm)) {
                    it1.remove();
                    it2.remove(); //cm will not used for further matchings
                    if (cn.children.isEmpty()) {
                        cm.leafOfPatterns.addAll(cn.leafOfPatterns);
                        cm.labels.entrySet().addAll(cn.labels.entrySet());
                    } else {
                        mergePatterns(cm, cn);
                    }
                    break;
                }
            }
        }

        //now in cn we have the nodes which were not already children of m
        m.children.addAll(n.children);
    }

    public PennTreebankPatternMatcher2() throws Exception {
        Pattern pattern = Pattern.compile(".*\\.prn");
        final Collection<String> list = getResources(pattern);
        int totalPatternsNode=0;
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
                if (p.root.values.size() != 1 || !p.root.values.contains("ROOT")) {
                    throw new Exception("Pattern in " + fileName + " starts with " + p.root.values + ". All the patterns are supposed to start with ROOT");
                }
                totalPatternsNode+=p.root.countNodes();
                p.root.print(0);
                patterns.put(p.name, p);
                if (rootOfMergedPatterns == null) {
                    rootOfMergedPatterns = p.root;
                } else {
                    mergePatterns(rootOfMergedPatterns, p.root);
                }
                numberOfLeaves.put(p.name, p.numberOfLeaves);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println("Merged patterns");
        rootOfMergedPatterns.print(0);
        System.out.println("Nodes in the merged patterns: "+rootOfMergedPatterns.countNodes());
        System.out.println("Nodes in all the patterns: "+totalPatternsNode);
    }

    public HashMap<PennTreebankPattern, SyntacticTree> match(String s) throws Exception {
        SyntacticTree st = parser.parse(s);
        HashMap<PennTreebankPattern, SyntacticTree> res = new HashMap<>();
        return res;
    }

    public static void main(String[] args) throws Exception {
        PennTreebankPatternMatcher2 matcher = new PennTreebankPatternMatcher2();
    }
}
