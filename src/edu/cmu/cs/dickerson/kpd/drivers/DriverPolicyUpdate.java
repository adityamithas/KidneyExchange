package edu.cmu.cs.dickerson.kpd.drivers;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import edu.cmu.cs.dickerson.kpd.solver.CycleFormulationCPLEXSolver;
import edu.cmu.cs.dickerson.kpd.solver.exception.SolverException;
import edu.cmu.cs.dickerson.kpd.solver.solution.Solution;
import edu.cmu.cs.dickerson.kpd.structure.Cycle;
import edu.cmu.cs.dickerson.kpd.structure.Edge;
import edu.cmu.cs.dickerson.kpd.structure.Pool;
import edu.cmu.cs.dickerson.kpd.structure.VertexAltruist;
import edu.cmu.cs.dickerson.kpd.structure.VertexPair;
import edu.cmu.cs.dickerson.kpd.structure.alg.CycleGenerator;
import edu.cmu.cs.dickerson.kpd.structure.alg.CycleMembership;
import edu.cmu.cs.dickerson.kpd.structure.generator.SparseUNOSSaidmanPoolGenerator;
import edu.cmu.cs.dickerson.kpd.dynamic.arrivals.ExponentialArrivalDistribution;
import edu.cmu.cs.dickerson.kpd.solver.GreedyPackingSolver;
import edu.cmu.cs.dickerson.kpd.solver.approx.CyclesSampleChainsIPPacker;
import edu.cmu.cs.dickerson.kpd.structure.alg.FailureProbabilityUtil;
import edu.cmu.cs.dickerson.kpd.structure.generator.PoolGenerator;


public class DriverPolicyUpdate {
	static final int CHAIN_CAP = 4;
	static final int CYCLE_CAP = 3;
	static final int EXPECTED_PAIRS = 10;
	static final int EXPECTED_ALTRUISTS = 5;
	static final int ITERATIONS = 100;
	static final int MIN_POLICIES = 5;
	static final double DEATH = 0.000580725433182381168050643691;
	static final double PATIENCE = 0.02284;
	static final double RENEGE = .5;

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

		long startTime = System.currentTimeMillis();

		long rFailureSeed = System.currentTimeMillis();  // for experiments, set seed explicitly, e.g. "12345L" and record
		Random rFailure = new Random(rFailureSeed);
		long rEntranceSeed = System.currentTimeMillis() + 1L;
		Random rEntrance = new Random(rEntranceSeed);
		long rDepartureSeed = System.currentTimeMillis() + 2L;
		Random rDeparture = new Random(rDepartureSeed);

		PoolGenerator poolGen = new SparseUNOSSaidmanPoolGenerator(rEntrance);
		ExponentialArrivalDistribution m = new ExponentialArrivalDistribution(1.0/EXPECTED_PAIRS);
		ExponentialArrivalDistribution a = new ExponentialArrivalDistribution(1.0/EXPECTED_ALTRUISTS);
		Pool pool = new Pool(Edge.class);
		ArrayList<Cycle> matches = new ArrayList<Cycle>();

		int totalSeen = 0;
		int totalMatched = 0;
		int totalFailedMatches = 0;
		int totalDeceased = 0;


		TreeMap<String, Double> policy = new TreeMap<String, Double> ();

		// Creates initial policy with each blood-type being mapped to random real number [0,1)
		for (int k = 0; k < bloodTypes.values().length; k++) {
			String blood_type = bloodTypes.values()[k].toString();
			policy.put(blood_type, Math.random());
		}


		for (int i = 1; i <= ITERATIONS; i++) {


///////////////////////////////////////////////////////////////////////

			// Add new vertices to the pool
			int pairs = m.draw().intValue();
			int alts = a.draw().intValue();
			System.out.println("ITERATION: "+i+"\t"+pairs+" new pairs and "+alts+" new altruist(s)");
			if(pairs > 0){
				totalSeen += poolGen.addVerticesToPool(pool, pairs, alts)
						.size();
			}
			FailureProbabilityUtil.setFailureProbability(pool, FailureProbabilityUtil.ProbabilityDistribution.CONSTANT, rFailure);

			// Remove all pairs where the patient dies
			ArrayList<VertexPair> rm = new ArrayList<VertexPair>();
			for (VertexPair v : pool.getPairs()) {
				if (rDeparture.nextDouble() <= DEATH) {
					totalDeceased++;
					Iterator<Cycle> matchIterator = matches.iterator();
					while (matchIterator.hasNext()) {
						Cycle c = matchIterator.next();
						if (Cycle.getConstituentVertices(c, pool).contains(v)) {
							matchIterator.remove();
						}
					}
					rm.add(v);
				}
			}
			for(VertexPair v : rm){
				pool.removeVertex(v);
			}
			// Remove all altruists that run out of patience
			Iterator<VertexAltruist> aiter = pool.getAltruists().iterator();
			ArrayList<VertexAltruist> toRemove = new ArrayList<VertexAltruist>();
			while (aiter.hasNext()) {
				VertexAltruist alt = aiter.next();
				if (rDeparture.nextDouble() <= PATIENCE) {
					toRemove.add(alt);
				}
			}
			pool.removeAllVertices(toRemove);

			// Remove edges in matchings
			Iterator<Cycle> iter = matches.iterator();
			while(iter.hasNext()) {
				Cycle ci = iter.next();
				boolean fail = false;
				for (Edge e : ci.getEdges()) {
					if (rFailure.nextDouble() <= e.getFailureProbability()) {
						iter.remove();
						totalFailedMatches++;
						fail = true;
						break;
					}
				}
				if(fail){
					continue;
				}
				//All edges in the Cycle remain, so we have a match!
				else {
					// We matched a chain, now we have to make the last
					// donor a bridge donor with some probability
					if (Cycle.isAChain(ci, pool)) {
						ArrayList<VertexPair> trm = new ArrayList<VertexPair>();
						List<Edge> le = new ArrayList<Edge>();
						for(Edge e : ci.getEdges()){
							le.add(e);
						}
						Collections.reverse(le);
						le.remove(le.size()-1);
						for(Edge e : le){
							// The bridge donor reneged, we stop the chain here
							if (rDeparture.nextDouble() <= RENEGE) {
								trm.add((VertexPair)pool.getEdgeTarget(e));
								break;
							} else {
								VertexPair bridge = (VertexPair)pool.getEdgeTarget(e);
								trm.add(bridge);
								VertexAltruist bridgeDonor = new VertexAltruist(bridge.getID(),
										bridge.getBloodTypeDonor());
								pool.addAltruist(bridgeDonor);
							}
							totalMatched++;
						}
						pool.removeAllVertices(trm);
					}
					else{
						// Remove all vertices in the match from the pool
						totalMatched += Cycle.getConstituentVertices(ci, pool).size();
						pool.removeAllVertices(Cycle.getConstituentVertices(ci, pool));
					}
					// Remove this match from our current set of matchings
					iter.remove();
				}
			}

			// Match the vertex pairs in the pool
			CycleGenerator cg = new CycleGenerator(pool);
			List<Cycle> cycles = cg.generateCyclesAndChains(CYCLE_CAP, 0, true);
			//CycleMembership membership = new CycleMembership(pool, cycles);
			//CyclesSampleChainsIPPacker optIPS = new CyclesSampleChainsIPPacker(pool, cycles, 100, CHAIN_CAP, true);

			try{
				//Solution optSolIP = optIPS.solve();
				GreedyPackingSolver s = new GreedyPackingSolver(pool);
				List<Cycle> reducedCycles = (new CycleGenerator(pool)).generateCyclesAndChains(3, 0, true);
				Solution sol = s.solve(1, new CyclesSampleChainsIPPacker(pool, reducedCycles, 100, CHAIN_CAP, true), Double.MAX_VALUE);
				for(Cycle c : sol.getMatching()){
					matches.add(c);
				}
			}
			catch(SolverException e){
				e.printStackTrace();
				System.exit(-1);
			}

			System.out.println(totalSeen + " vertices were seen");
			System.out.println(totalMatched + " vertices were matched");
			System.out.println(totalFailedMatches + " matches failed");
			System.out.println(totalDeceased + " patients died");

			long endTime = System.currentTimeMillis();

			System.out.println(endTime-startTime);





//////////////////////////////////////////









			//Create abstraction of Graph @ time t
			//state0 (st_1) = number of each bloodtype
			System.out.println("__________________________1_______________________");
			
			pool.createBloodTypeAbstraction();
			Collection<Integer> c = pool.getMap().values();
			int [] array = new int [c.size()];
			int counter = 0;
			for (int val : c) {
				array[counter] = val;
				counter++;
				System.out.print(val + ", ");
			}
			exportToCsv(array,"st_1.csv");
			
			//Generate Graph @ time t+1
			//state(t+1) (st) = number of each bloodtype in following state
			reweightGraph(pool, policy);
			System.out.println();
			System.out.println("__________________________2________________________");
			
			pool.createBloodTypeAbstraction();
			c = pool.getMap().values();
			array = new int [c.size()];
			counter = 0;
			for (int val : c) {
				array[counter] = val;
				counter++;
			}
			exportToCsv(array,"st.csv");

			//System call to run NNet 
			//NNet will return a policy "weights.csv"
			try {
				System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
				//Process p = Runtime.getRuntime().exec("python nnet.py");
				Process p = Runtime.getRuntime().exec("python C:\\Users\\Aditya\\Documents\\GitHub\\KidneyExchange\\src\\edu\\cmu\\cs\\dickerson\\kpd\\drivers\\nnet.py");
				try {
					InputStream inp = p.getErrorStream();
					//InputStream a = p.getInputStream();
					int in = inp.read();

					while (in > 0) {
						System.out.print((char)in);
						in = inp.read();
					}
					p.waitFor();
					System.out.println("EXIT: " + p.exitValue());
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

			//System.out.println("TIME FOR A MAX MATCHING");
			
			//Conduct max matching to get objective values 
			Solution optSolIP = null;
			CycleMembership membership = new CycleMembership(pool, matches);
			CycleFormulationCPLEXSolver optIPS = new CycleFormulationCPLEXSolver(pool, matches, membership);
			try {
				optSolIP = optIPS.solve();
			} catch (SolverException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			//System.out.println("3:        " + optSolIP.getObjectiveValue());
			
			if (optSolIP != null) {
				Double value = optSolIP.getObjectiveValue();
				exportToCsv(new double [] {value}, "obj_value.csv");
			}


		}
	}

	/*
	 * Takes array as argument and exports its elements to a CSV
	 */
	public static void exportToCsv (int[] array, String filename) {
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
		for (int i = 0; i < array.length; i++) {				
			outfile.write(String.valueOf(array[i]));
			outfile.write(",");
		}
		outfile.close();
	}
	
	/*
	 * Takes array as argument and exports its elements to a CSV
	 */
	public static void exportToCsv (double[] array, String filename) {
		FileWriter pw = null;
		try {
			pw = new FileWriter(filename, true);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 

		//Writes each element of array to CSV
		for (int i = 0; i < array.length; i++) {				
			try {
				pw.write(String.valueOf(array[i]));
				pw.write(",");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		try {
			pw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
