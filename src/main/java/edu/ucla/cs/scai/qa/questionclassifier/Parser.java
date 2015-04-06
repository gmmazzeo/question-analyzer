/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucla.cs.scai.qa.questionclassifier;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.ucla.cs.scai.swim.qa.ontology.NamedEntityAnnotationResult;
import edu.ucla.cs.scai.swim.qa.ontology.Ontology;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 *
 * @author Giuseppe M. Mazzeo <mazzeo@cs.ucla.edu>
 */
public class Parser {

    StanfordCoreNLP pipelineTree;
    StanfordCoreNLP pipelineTokens;
    Ontology ontology;
    ArrayList<NamedEntityAnnotationResult> entityAnnotations;

    public Parser(Ontology ontology) {
        this.ontology = ontology;
        Properties propsTokens = new Properties();
        propsTokens.put("annotators", "tokenize, ssplit, pos, lemma, ner, regexner, parse, dcoref");
        Properties propsTree = new Properties();
        propsTree.put("annotators", "tokenize, ssplit, parse");
        pipelineTree = new StanfordCoreNLP(propsTree);
        pipelineTokens = new StanfordCoreNLP(propsTokens);
    }

    public SyntacticTree parse(String text) throws Exception {
        entityAnnotations = (ArrayList<NamedEntityAnnotationResult>) ontology.annotateNamedEntities(text);
        //char[] c=text.toCharArray();
        //int j=c.length-1;
        //while (j>=0 && (c[j]=='.' || c[j]=='!' || c[j]=='?' || c[j]==' ')) {
        //    j--;
        //}
        //text=String.copyValueOf(c, 0, j+1);
        Annotation qaTree = new Annotation(text);
        pipelineTree.annotate(qaTree);
        Annotation qaTokens = new Annotation(text);
        pipelineTokens.annotate(qaTokens);
        List<CoreMap> qssTree = qaTree.get(CoreAnnotations.SentencesAnnotation.class);
        List<CoreMap> qssTokens = qaTokens.get(CoreAnnotations.SentencesAnnotation.class);

        if (qssTree.isEmpty()) {
            throw new Exception("Empty question");
        }
        if (qssTree.size() > 1) {
            throw new Exception("One sentence per question, please!");
        }
        CoreMap qsTree = qssTree.get(0);
        CoreMap qsTokens = qssTokens.get(0);

        SyntacticTree qt = new SyntacticTree(qsTree, qsTokens, entityAnnotations);

        //qt.compactNamedEntities();
        return qt;
    }

}
