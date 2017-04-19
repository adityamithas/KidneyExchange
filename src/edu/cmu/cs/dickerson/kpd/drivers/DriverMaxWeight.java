package edu.cmu.cs.dickerson.kpd.drivers;

import java.io.FileNotFoundException;
import java.util.Random;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import edu.cmu.cs.dickerson.kpd.solver.CycleFormulationCPLEXSolver;
import edu.cmu.cs.dickerson.kpd.solver.exception.SolverException;
import edu.cmu.cs.dickerson.kpd.solver.solution.Solution;
import edu.cmu.cs.dickerson.kpd.structure.Pool;
import edu.cmu.cs.dickerson.kpd.structure.alg.CycleGenerator;
import edu.cmu.cs.dickerson.kpd.structure.alg.CycleMembership;
import edu.cmu.cs.dickerson.kpd.structure.generator.SparseUNOSSaidmanPoolGenerator;
import edu.cmu.cs.dickerson.kpd.structure.Cycle;

public class DriverMaxWeight {
	static String [][] matrix;
	static int currentRow = 0;

	public static void main(String[] args) {
		matrix = new String [400][21]; // should be 400x21 if we uncomment lines 30-32
		currentRow = 0;
		
		runSimulation(100, 0);
		runSimulation(100, 10);
		runSimulation(100, 20);
		runSimulation(100, 50);
		exportToCsv(matrix);
	}

	/*
	 * Creates graph of 100 pairs and numAlts altruists, determines value of that particular graph.
	 * Does this numberOfRuns times.
	 */
	public static void runSimulation (int numberOfRuns, int numAlts) {
		int numPairs = 100;
		Solution optSolIP = null;
		Random r = new Random();
		int cycleCap = 3;
		int chainCap = 4;
		int counter = 0;
		Integer occ = -1;// Number of occurrences a key maps to

		//While there are still runs to make, continue simulation
		while (counter < numberOfRuns) {
			//Every even number index is a blood-type, every odd number is number of occurrences 
			//the blood-type on left generated in graph
			String [] forCsv = new String [41];
			
			//Initialize every occurrence to having occurred zero times so we don't end up with foreign values in CSV
			for (int k = 1; k < forCsv.length; k+=2) {
				forCsv[k] = String.valueOf(0);
			}

			
			// Generates a value representing max weight disjoint sets of this respective Graph
			SparseUNOSSaidmanPoolGenerator gen = new SparseUNOSSaidmanPoolGenerator(r);
			Pool pool = gen.generate(numPairs, numAlts);
			CycleGenerator cg = new CycleGenerator(pool);
			List<Cycle> cycles = cg.generateCyclesAndChains(cycleCap, chainCap, false);
			CycleMembership membership = new CycleMembership(pool, cycles);
			CycleFormulationCPLEXSolver optIPS = new CycleFormulationCPLEXSolver(pool, cycles, membership);
			try {
				optSolIP = optIPS.solve();
			} catch (SolverException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			Double value = optSolIP.getObjectiveValue();
			
			// Generates a map representation of graph
			pool.createBloodTypeAbstraction();
			TreeMap<String, Integer> listOfTypes = pool.getMap();
			Set<String> keys = listOfTypes.keySet();
			
			// For the sake of being consistent; I realize this is terrible
			forCsv[0] = "OA";
			forCsv[2] = "OB";
			forCsv[4] = "OO";
			forCsv[6] = "OAB";
			forCsv[8] = "AA";
			forCsv[10] = "AB";
			forCsv[12] = "AO";
			forCsv[14] = "AAB";
			forCsv[16] = "BA";
			forCsv[18] = "BB";
			forCsv[20] = "BO";
			forCsv[22] = "BAB";
			forCsv[24] = "ABA";
			forCsv[26] = "ABB";
			forCsv[28] = "ABO";
			forCsv[30] = "ABAB";
			forCsv[32] = "XA";
			forCsv[34] = "XB";
			forCsv[36] = "XO";
			forCsv[38] = "XAB";

			// Place occurrences of each blood-type next to that specific type in array representation of map
			// For the sake of consistency across graphs
			// I realize this is also terrible
			for (String key : keys) {	
				String combo = key;
				occ = listOfTypes.get(key);

				if (combo.equals("OA")) {
					forCsv[1] = String.valueOf(occ);
				}
				if (combo.equals("OB")) {
					forCsv[3] = String.valueOf(occ);
				}
				if (combo.equals("OO")) {
					forCsv[5] = String.valueOf(occ);
				}
				if (combo.equals("OAB")) {
					forCsv[7]= String.valueOf(occ);
				}
				if (combo.equals("AA")) {
					forCsv[9]= String.valueOf(occ);
				}
				if (combo.equals("AB")) {
					forCsv[11] = String.valueOf(occ);
				}
				if (combo.equals("AO")) {
					forCsv[13]= String.valueOf(occ);
				}
				if (combo.equals("AAB")) {
					forCsv[15]= String.valueOf(occ);
				}
				if (combo.equals("BA")) {
					forCsv[17] = String.valueOf(occ);
				}
				if (combo.equals("BB")) {
					forCsv[19] = String.valueOf(occ);
				}
				if (combo.equals("BO")) {
					forCsv[21]= String.valueOf(occ);
				}
				if (combo.equals("BAB")) {
					forCsv[23] = String.valueOf(occ);
				}
				if (combo.equals("ABA")) {
					forCsv[25]= String.valueOf(occ);
				}
				if (combo.equals("ABB")) {
					forCsv[27]= String.valueOf(occ);
				}
				if (combo.equals("ABO")) {
					forCsv[29] = String.valueOf(occ);
				}
				if (combo.equals("ABAB")) {
					forCsv[31] = String.valueOf(occ);
				}
				if (combo.equals("XA")) {
					forCsv[33]= String.valueOf(occ);
				}
				if (combo.equals("XB")) {
					forCsv[35]= String.valueOf(occ);
				}
				if (combo.equals("XO")) {
					forCsv[37]= String.valueOf(occ);
				}
				if (combo.equals("XAB")) {
					forCsv[39] = String.valueOf(occ);
				}
			}
			forCsv[40] = String.valueOf(value);
			
			for (int x = 1; x < forCsv.length; x+=2) {
				matrix[currentRow][x/2] = forCsv[x];
			}
			matrix[currentRow][20] = String.valueOf(value);
			
			currentRow ++;// Update which row in matrix to fill values into from array
			counter ++;// Update number of times 
		}
	}

	/*
	 * Takes 2D array as argument and exports its elements to a CSV - each row of matrix is a row in CSV.
	 */
	public static void exportToCsv (String [][] arg) {
		//create a File class object
		java.io.File weightsCSV = new java.io.File("weights.csv");

		//Create a Printwriter text output stream and link it to the CSV File
		java.io.PrintWriter outfile = null;
		try {
			outfile = new java.io.PrintWriter(weightsCSV);
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		//Writes each element of matrix to CSV
		for (int i = 0; i < arg.length; i++) {
			for (int j = 0; j < arg[i].length; j++) {
				outfile.write(arg[i][j]);
				outfile.write(",");
			}
			outfile.write("\n");
		}
		outfile.close();
	}

}
