package webservicediscovery;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import webservicediscovery.HungarianAlgorithm;
import webservicediscovery.ServiceDescMatchingEngine;


public class FunctionMatching 
{
    String base_path = "http://127.0.0.1";
    int w1=1, w2=2, w3=3, w4=4;
 
    public int match(String query_param, String adv_param) throws OWLOntologyCreationException {
        try {
            int start = base_path.length();
            if(!(query_param.substring(start, query_param.indexOf('#')).equals(adv_param.substring(start, adv_param.indexOf('#'))))) {
                return w4;
            }
            // File file = new File(query_param.substring(0, query_param.indexOf('#')));
            InputStream file = new URL(query_param.substring(0, query_param.indexOf('#'))).openStream();
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            OWLOntology ont = manager.loadOntologyFromOntologyDocument(file);
            for (OWLClass owl_class : ont.getClassesInSignature()) {
                if(owl_class.toStringID().equals("http://www.w3.org/2002/07/owl#Thing")||owl_class.toStringID().equals("http://www.w3.org/2002/07/owl#Property")) {
                    continue;
                }
                else {
                    String adv = adv_param.replace(base_path, "http://127.0.0.1");
                    String query = query_param.replace(base_path, "http://127.0.0.1");
                    if(adv.equals(owl_class.toStringID())) {
                        //Check for OutA equivalent to OutR
                        if(adv.equals(query)) {
                            return w1;
                        }
                        for(OWLClassExpression equiv_class : owl_class.getEquivalentClasses(ont)) {
                            if(!equiv_class.isAnonymous()) {
                                if(query.equals(equiv_class.asOWLClass().toStringID())) {
                                    return w1;
                                }
                            }
                        }

                        //Check for OutA is superclass of OutR
                        //Plus check for OutA subsumes OutR
                        int size;
                        List<OWLClassExpression> subsumes = new ArrayList<>();
                        subsumes.addAll(owl_class.getSubClasses(ont));
                        size = subsumes.size();
                        for(int i=0;i<subsumes.size();i++) {
                            if(!subsumes.get(i).isAnonymous()) {
                                if(query.equals(subsumes.get(i).asOWLClass().toStringID())) {
                                    if(i<size) {
                                        return w1;
                                    }
                                    else {
                                        return w2;
                                    }
                                }
                                else {
                                    subsumes.addAll(subsumes.get(i).asOWLClass().getSubClasses(ont));
                                }
                            }
                        }

                        //Check for OutR subsumes OutA
                        List<OWLClassExpression> subsumed = new ArrayList<>();
                        subsumed.addAll(owl_class.getSuperClasses(ont));
                        for(int i=0;i<subsumed.size();i++) {
                            if(!subsumed.get(i).isAnonymous()) {
                                if(query.equals(subsumed.get(i).asOWLClass().toStringID())) {
                                    return w3;
                                }
                                else {
                                    subsumed.addAll(subsumed.get(i).asOWLClass().getSuperClasses(ont));
                                }
                            }
                        }
                        //Every match failed
                        return w4;
                    }
                }
            }
            return w4;
        } catch (Exception ex) {
            ex.printStackTrace();
        } 
        return 5;
    }
    
    public void print_graph(int[][] graph) {
        for(int i=0;i<graph.length;i++) {
            for(int j=0;j<graph[i].length;j++) {
                System.out.print(graph[i][j]+"  ");
            }
            System.out.println();
        }
    }
    
    public double get_best_match(String adv_file, List<String> req_in_params, List<String> req_out_params, List<String> adv_in_params, List<String> adv_out_params)
    {
        try 
        {
            int irow = adv_in_params.size(), icol = req_in_params.size();
            int orow = adv_out_params.size(), ocol = req_out_params.size();
            int isize, osize;
            isize = irow>=icol?irow:icol;
            osize = orow>=ocol?orow:ocol;
            int[][] in_spp = new int[isize][isize];
            int[][] out_spp = new int[osize][osize];
            int [][] in_temp = new int[in_spp.length][];
            int [][] out_temp = new int[out_spp.length][];
            int in_score, out_score;
            
            for(int[] row: in_spp) {
                Arrays.fill(row, 5);
            }
            for(int[] row: out_spp) {
                Arrays.fill(row, 5);
            }
            
            //For input parameters
            for(int i=0;i<irow;i++) {
                for(int j=0;j<icol;j++) {
                    in_spp[i][j] = match(req_in_params.get(j),adv_in_params.get(i));
                }
            }
            
            for(int i = 0; i < in_spp.length; i++) {
                in_temp[i] = in_spp[i].clone();
            }

            HungarianAlgorithm hungarian = new HungarianAlgorithm();
            int in_res[][] = hungarian.computeAssignments(in_temp);
            in_score = 0;
            for(int i=0;i<in_res.length;i++) {
                in_score += in_spp[in_res[i][0]][in_res[i][1]];
            }
            
            //For output parameters
            for(int i=0;i<orow;i++) {
                for(int j=0;j<ocol;j++) {
                    out_spp[i][j] = match(req_out_params.get(j),adv_out_params.get(i));
                }
            }
            
            for(int i = 0; i < out_spp.length; i++) {
                out_temp[i] = out_spp[i].clone();
            }

            int out_res[][] = hungarian.computeAssignments(out_temp);
            out_score = 0;
            for(int i=0;i<out_res.length;i++) {
                out_score += out_spp[out_res[i][0]][out_res[i][1]];
            }

            return ((0.4*in_score)+(0.6*out_score));
            
        } 
        catch(Exception ex) 
        {
            ex.printStackTrace();
        }
        return 0.0;
    }

 }  
    
/*public void get_files() throws IOException {
    File folder = new File("C:\\xampp\\htdocs\\services\\1.1");
    File[] listOfFiles = folder.listFiles();
    PrintWriter write = new PrintWriter(new FileWriter("advertisements.txt"));

    for (int i = 0; i < listOfFiles.length; i++) {
        if (listOfFiles[i].isFile()) {
            System.out.println(listOfFiles[i].getName());
            write.print(listOfFiles[i].getName()+"\n");
        } 
    }
    write.close();
}*/

