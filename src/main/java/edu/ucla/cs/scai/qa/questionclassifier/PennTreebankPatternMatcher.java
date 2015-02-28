/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cs.scai.qa.questionclassifier;

import edu.stanford.nlp.ling.CoreLabel;
import edu.ucla.cs.scai.swim.qa.ontology.QueryConstraint;
import edu.ucla.cs.scai.swim.qa.ontology.QueryModel;
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
public class PennTreebankPatternMatcher {

    private static final HashMap<String, PennTreebankPattern> patterns = new HashMap<>();

    private Parser parser = new Parser();

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

    public PennTreebankPatternMatcher() {
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
                        s = s + "\n";
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

    public HashMap<PennTreebankPattern, SyntacticTree> match(String s) throws Exception {
        SyntacticTree st = parser.parse(s);
        HashMap<PennTreebankPattern, SyntacticTree> res = new HashMap<>();
        for (PennTreebankPattern pattern : patterns.values()) {
            if (pattern.name.equals("GIVE_ME_LIST_OF_FOCUS")) {
                System.out.print("");
            }
            if (st.match(pattern)) {
                res.put(pattern, st);
            }
        }
        return res;
    }

    public static void main(String[] args) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(PennTreebankPatternMatcher.class.getResourceAsStream("/qald3")));
        String l = in.readLine();
        int tot = 0;
        int ok = 0;
        PennTreebankPatternMatcher matcher = new PennTreebankPatternMatcher();
        HashMap<String, ArrayList<String>> questions = new HashMap<>();        
        while (l != null && l.length() > 0) {
            if (!l.startsWith("%")) {
                System.out.println();
                tot++;
                System.out.println("\n" + l);
                try {
                    if (l.equals("Give me a list of all American inventions.")) {
                        System.out.print("");
                    }
                    HashMap<PennTreebankPattern, SyntacticTree> matches = matcher.match(l);
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
                    for (PennTreebankPattern pattern : matches.keySet()) {
                        System.out.println(pattern.name);
                        if (pattern.name.equals("GIVE_ME_FOCUS")) {
                            System.out.print("");
                        }
                        QueryResolver qr = new QueryResolver(matches.get(pattern));
                        for (QueryModel qm : qr.resolveIQueryModels(pattern)) {
                            System.out.println();
                            System.out.println(qm);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
            l = in.readLine();
        }
        System.out.println(ok + "/" + tot);
        for (String t : new TreeSet<String>(questions.keySet())) {
            System.out.println("==========================================");
            System.out.println(t);
            System.out.println("------------------------------------------");
            for (String qs : questions.get(t)) {
                System.out.print(qs);
                for (String prn : new TreeSet<String>(questions.keySet())) {
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
    }
}
