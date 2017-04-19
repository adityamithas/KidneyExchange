package edu.cmu.cs.dickerson.kpd.drivers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;

import edu.cmu.cs.dickerson.kpd.dynamic.arrivals.ExponentialArrivalDistribution;
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
import edu.cmu.cs.dickerson.kpd.structure.alg.FailureProbabilityUtil;
import edu.cmu.cs.dickerson.kpd.structure.generator.PoolGenerator;
import edu.cmu.cs.dickerson.kpd.structure.generator.SparseUNOSSaidmanPoolGenerator;

public class DynamicGraph {
	// Probabilities generated based on a match frequency of 1 day
	static final int CHAIN_CAP = 4;
	static final int CYCLE_CAP = 3;
	static final int EXPECTED_PAIRS = 10;
	static final int EXPECTED_ALTRUISTS = 5;
	static final int ITERATIONS = 3;
	static final double DEATH = 0.000580725433182381168050643691;
	static final double PATIENCE = 0.02284;
	static final double RENEGE = .5;
	
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
	
	public Pool returnPool () {
		return pool;
	}

	public ArrayList<Double> returnMatches () {
		
		ArrayList<Cycle> matches = new ArrayList<Cycle>();
		ArrayList<Double> values = new ArrayList<Double>();

		int totalSeen = 0;
		int totalMatched = 0;
		int totalFailedMatches = 0;
		int totalDeceased = 0;

		for (int i = 1; i <= ITERATIONS; i++) {
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
			for (VertexPair v : pool.getPairs()) {
				if (rDeparture.nextDouble() <= DEATH) {
					totalDeceased++;
					pool.removeVertex(v);
					for (Cycle c : matches) {
						if (Cycle.getConstituentVertices(c, pool).contains(v)) {
							matches.remove(c);
						}
					}
				}
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
				else{
					totalMatched += Cycle.getConstituentVertices(ci, pool).size();
					//We matched a chain, now we have to make the last donor a bridge donor with some probability
					if (Cycle.isAChain(ci, pool)) {
						//The bridge donor reneged, remove all vertices from the pool
						if(rDeparture.nextDouble() <= RENEGE){
							pool.removeAllVertices(Cycle.getConstituentVertices(ci, pool));
						}
						else{
							VertexPair bridge = null;
							ListIterator<Edge> reverseEdgeIt = ci.getEdges().listIterator(ci.getEdges().size());
							while(reverseEdgeIt.hasPrevious()) {
								if(pool.getEdgeSource(reverseEdgeIt.previous()).isAltruist()) {
									bridge = (VertexPair) pool.getEdgeSource(reverseEdgeIt.previous()); //This is now the vertex previous to the altruist
								}
							}
							VertexAltruist bridgeDonor = new VertexAltruist(bridge.getID(),bridge.getBloodTypeDonor());
							pool.addAltruist(bridgeDonor);
						}
					}
					//Remove all vertices in the match from the pool
					pool.removeAllVertices(Cycle.getConstituentVertices(ci, pool));
					
					//Remove this match from our current set of matchings
					iter.remove();
				}
			}

			// Match the vertex pairs in the pool
			CycleGenerator cg = new CycleGenerator(pool);
			List<Cycle> cycles = cg.generateCyclesAndChains(CYCLE_CAP, CHAIN_CAP, true);
			CycleMembership membership = new CycleMembership(pool, cycles);
			CycleFormulationCPLEXSolver optIPS = new CycleFormulationCPLEXSolver(pool, cycles, membership);
			try{
				Solution optSolIP = optIPS.solve();
				values.add(optSolIP.getObjectiveValue());
				for(Cycle c : optSolIP.getMatching()){
					matches.add(c);
				}
			}
			catch(SolverException e){
				e.printStackTrace();
				System.exit(-1);
			}
			
//			System.out.println(totalSeen + " vertices were seen");
//			System.out.println(totalMatched + " vertices were matched");
//			System.out.println(totalFailedMatches + " matches failed");
//			System.out.println(totalDeceased + " patients died");
			
		}
		return values;
	}

}