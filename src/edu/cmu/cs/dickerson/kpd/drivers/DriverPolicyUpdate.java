package edu.cmu.cs.dickerson.kpd.drivers;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import edu.cmu.cs.dickerson.kpd.solver.CycleFormulationCPLEXSolver;
import edu.cmu.cs.dickerson.kpd.solver.exception.SolverException;
import edu.cmu.cs.dickerson.kpd.solver.solution.Solution;
import edu.cmu.cs.dickerson.kpd.structure.Cycle;
import edu.cmu.cs.dickerson.kpd.structure.Edge;
import edu.cmu.cs.dickerson.kpd.structure.Pool;
import edu.cmu.cs.dickerson.kpd.structure.Vertex;
import edu.cmu.cs.dickerson.kpd.structure.VertexAltruist;
import edu.cmu.cs.dickerson.kpd.structure.VertexPair;
import edu.cmu.cs.dickerson.kpd.structure.alg.CycleGenerator;
import edu.cmu.cs.dickerson.kpd.structure.alg.CycleMembership;
import edu.cmu.cs.dickerson.kpd.structure.generator.SparseUNOSSaidmanPoolGenerator;
import edu.cmu.cs.dickerson.kpd.structure.real.UNOSDonor;
import edu.cmu.cs.dickerson.kpd.structure.real.UNOSPair;
import edu.cmu.cs.dickerson.kpd.structure.real.sampler.UNOSSampler;

public class DriverPolicyUpdate {
	static final int CHAIN_CAP = 4;
	static final int CYCLE_CAP = 3;
	static final int EXPECTED_PAIRS = 10;
	static final int EXPECTED_ALTRUISTS = 5;
	static final int ITERATIONS = 100;
	static final int MIN_POLICIES = 5;

	public enum bloodTypes {
		OA, OB, OO, OAB, AA, AB, AO, AAB, BA, BB, BO, BAB, ABA, ABB, ABO, ABAB, XA, XB, XO, XAB;
	}

	public static void updatePolicy (TreeMap<String, Double> curPolicy, ArrayList<Double> curState) {

	}

	public static void reweightGraph (Pool pool, TreeMap<String, Double> policy) {
		// Generate every vertex-vertex pair
		for(VertexPair vN : pool.getPairs()) { 
			for(VertexPair vO : pool.getPairs()) {

				if(vN.equals(vO)) { continue; }  // No self-edges

				// Convert BloodType attribute of each vertex to a String
				String vN_bloodtype = vN.getBloodTypePatient().toString() +
						vN.getBloodTypeDonor().toString();
				String vO_bloodtype = vO.getBloodTypePatient().toString() +
						vO.getBloodTypeDonor().toString();

				// Determine weight associated with each BloodType from current policy
				double nWeight = policy.get(vN_bloodtype);
				double oWeight = policy.get(vO_bloodtype);

				// Generate every edge between two vertices
				for (Edge e : pool.getAllEdges(vN, vO)) {
					// Adjust weight of the edge
					double newEdgeWeight = 1 - (0.5)*(nWeight + oWeight);
					pool.setEdgeWeight(e, newEdgeWeight);
				}
			}
		}
	}

	public static void main (String [] args) {
		int numberOfRuns = 0;
		ArrayList<Double> network = new ArrayList<Double> ();
		Double avg = 0.0;
		Double previousAvg = avg;
		TreeMap<String, Double> policy = new TreeMap<String, Double> ();

		// Creates initial policy with each blood-type being mapped to random real number [0,1)
		for (int k = 0; k < bloodTypes.values().length; k++) {
			String blood_type = bloodTypes.values()[k].toString();
			policy.put(blood_type, Math.random());
		}

		DynamicGraph graph = new DynamicGraph();//Generate G_t0

		// While we're trying to get a better policy (stop once max weight matching of 2 consecutive simulations is within 1%).
		// We will generate at least some number of policies before settling.
		while ( numberOfRuns < MIN_POLICIES || (avg <= 1.01*previousAvg && avg >= .99*previousAvg) ) {
			previousAvg = avg;
			avg = 0.0;

			//Create abstraction of Graph @ time t
			exportToCsv(graph.returnPool().getMap().values().toArray(),"number_ofeach_bloodtype.csv");

			//Generate Graph @ time t+1
			reweightGraph(graph.returnPool(), policy);
			exportToCsv(graph.returnPool().getMap().values().toArray(),"newstate_number_ofeach_bloodtype.csv");

			//System call to run NNet 
			//NNet will return a policy "weights.csv"
			try {
				Process p = Runtime.getRuntime().exec("python test.py");
			} catch (IOException e) {
				e.printStackTrace();
			}

			//Conduct max matching to get objective values 
			network =  graph.returnMatches();
			exportToCsv(network.toArray(), "obj_value.csv");
			
			//Check if we have reached loop ending condition
			for (Double individualValue : network) {
				avg += individualValue;
			}
			avg /= network.size();
			numberOfRuns++;
		}
	}

	/*
	 * Takes array as argument and exports its elements to a CSV
	 */
	public static void exportToCsv (Object[] arg, String filename) {
		//create a File class object
		java.io.File weightsCSV = new java.io.File(filename);

		//Create a Printwriter text output stream and link it to the CSV File
		java.io.PrintWriter outfile = null;
		try {
			outfile = new java.io.PrintWriter(weightsCSV);
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		//Writes each element of array to CSV
		for (int i = 0; i < arg.length; i++) {				
			outfile.write((Integer) arg[i]);
			outfile.write(",");
		}
		outfile.close();
	}
}
