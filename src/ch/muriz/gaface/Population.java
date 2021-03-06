package ch.muriz.gaface;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class Population //implements Runnable
{
    // Number of Individuals
    private int populationSize;
    // [0, ∞)
    private final float populationMutationRate = 0.1f;
    // (0, 1] : Fraction of population in tournament
    private final float tournamentFraction = 0.6f;
    // [0, ∞)
    private final float populationCrossoverRate = 0.9f;
    // Best fitness found in a generation so far
    private double bestFitness = 0;
    // Location to store status information
    private final String statusDirection;
    // File which holds the last generation's DNA
    private final String sourceGenerationFile = null;

    // Holds all the individuals from the current population
    private List<Individual> individuals = new ArrayList<Individual>();

    private Random rand;
    private Fitness fitness;
    private Phenotype phenotype;
    private CountDownLatch latch;

    public Population(int populationSize, String statusDirection) {
        rand = new Random();
        fitness = new Fitness();
        phenotype = new Phenotype();
        this.populationSize = populationSize;
        this.statusDirection = statusDirection;
        populationFromScratch();
    }

    //region Multiple Threads
    /*@Override
    public void run() {
        for(List<Integer> list : DNAList) {
            try {
                fitnessList.add(fitness.calculateFitness(list));
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
        latch.countDown();
    }*/
    //endregion

    /*
     * Creates new random population
     */
    public void populationFromScratch() {
        for(int i = 0; i < getPopulationSize(); i++) {
            Individual newIndividual = new Individual(null);
            individuals.add(newIndividual);
        }
    }

    /*
     * Returns the DNA List from the population
     */
    public List<List<Integer>> getDNAList() {
        List<List<Integer>> newDNAList = new ArrayList<>();
        if(individuals.size() != 0)
            for(Individual indiv : individuals)
                newDNAList.add(indiv.getDNA());
        else System.err.println("There are no individuals");

        return newDNAList;
    }

    /*
     * Evolve the population
     */
    public void evolvePopulation(boolean save, int populationNumber) throws IOException {
        // DNA List from the population
        List<List<Integer>> DNAList = getDNAList();
        // Holds all the fitnesses from every individual in the current population
        List<Double> fitnessList = new ArrayList<>();
        for(List<Integer> list : DNAList) {
            // TODO Very slow, let's fix this
            fitnessList.add(fitness.calculateFitness(list));
        }
        List<List<Number>> individualFitness = new ArrayList<>();
        // Helps us to create a list in form: [(fitness, 0), (fitness, 1), ...]
        List<Number> mergedList = new ArrayList<>();
        for(int i = 0; i < fitnessList.size(); i++) {
            mergedList.add(fitnessList.get(i));
            mergedList.add(i);
        }
        individualFitness = Utils.choppedList(mergedList, 2);
        Collections.sort(individualFitness, new Comparator<List<Number>>() {
            public int compare(List<Number> list1, List<Number> list2) {
                //Simple comparison here
                return Double.compare((double)list1.get(0), (double)list2.get(0));
            }
        });
        Collections.reverse(individualFitness);
        setBestFitness((double)individualFitness.get(0).get(0));

        // If requested, give a status update with the most fit individual from the old population
        // Also save the DNA for all individuals in the last population, and possible best generation
        if(save) {
            Individual bestSoFarIndividual = individuals.get((int)individualFitness.get(0).get(1));
            BufferedImage phenotypeImage = phenotype.createPhenotype(bestSoFarIndividual.getDNA());
            ImageIO.write(phenotypeImage, "png", new File(statusDirection + "/" + populationNumber + ".png"));
        }
        List<Individual> newPopulation = new ArrayList<>();
        List<Number> matingPair;
        List<List<Integer>> matingDNA = new ArrayList<>();
        for(int i = 0; i < (populationSize/2); i++) {
            matingPair = tournamentSelection(individualFitness);
            for(Number individualNumber : matingPair) {
                List<Integer> indiv = DNAList.get((int)individualNumber);
                matingDNA.add(indiv);
            }
            //Crossover the mating pair's DNA so many times according to crossover rate
            int crossovers = 0;
            if(populationCrossoverRate > 1.0f) {
                while(populationCrossoverRate - crossovers > 1) {
                    matingDNA = crossover(matingDNA);
                    crossovers++;
                }
            }
            if(Math.random() <= populationCrossoverRate - crossovers) {
                matingDNA = crossover(matingDNA);
            }

            // Mutate each mate's DNA so many times accorind to mutation rate
            for(int n = 0; n < 2; n++) {
                int mutations = 0;
                if(populationMutationRate > 1) {
                    while(populationMutationRate - mutations > 1) {
                        matingDNA.set(n, mutate(matingDNA.get(n)));
                        mutations++;
                    }
                }
                if(Math.random() <= populationMutationRate - mutations) {
                    matingDNA.set(n, mutate(matingDNA.get(n)));
                }
            }
            // Add a new individual based on the newly mutated/crossed over DNA to the population
            for(List<Integer> DNA : matingDNA) {
                Individual newIndiv = new Individual(DNA);
                newPopulation.add(newIndiv);
            }
        }
        individuals = new ArrayList<Individual>(newPopulation);
    }

    public List<List<Integer>> getSpecificDNAList(int index) {
        List<List<Integer>> specificDNAList = new ArrayList<>();
        if(individuals.size() != 0) specificDNAList.add(individuals.get(index).getDNA());
        else System.out.println("The specific individual doesn't exist. Available: 0 - "+(getPopulationSize()-1));
        return specificDNAList;
    }

    /*
     * Determines what individuals to mate
     * Based on a list of (fitness, individualNumber) tuples
     * Uses tournament selection
     */
    private List<Number> tournamentSelection(List<List<Number>> individualFitness) {
        List<List<Number>> newIndividualFitness = new ArrayList<>(individualFitness);
        int tournamentSize = (int)Math.ceil(newIndividualFitness.size() * tournamentFraction);
        if(tournamentSize == 1) tournamentSize = 2;

        // Create tournament
        int listCount = newIndividualFitness.size();
        List<List<Number>> tournament = new ArrayList<>();
        while(tournament.size() < tournamentSize) {
            int chosenIndividual = rand.nextInt(listCount);
            if(!tournament.contains(newIndividualFitness.get(chosenIndividual))) {
                tournament.add(newIndividualFitness.get(chosenIndividual));
            }
        }
        // Return two most fit individuals from tournament
        Collections.sort(tournament, new Comparator<List<Number>>() {
            public int compare(List<Number> list1, List<Number> list2) {
                //Simple comparison here
                return Double.compare((double)list1.get(0), (double)list2.get(0));
            }
        });
        Collections.reverse(tournament);
        List<Number> floatList1 = tournament.get(0);
        List<Number> floatList2 = tournament.get(1);
        List<Number> resultList = new ArrayList<>();
        resultList.add(floatList1.get(1)); resultList.add(floatList2.get(1));
        return resultList;
    }

    /*
     * Crosses over two pieces of mating DNA
     */
    private List<List<Integer>> crossover(List<List<Integer>> matingDNA) {
        int pivot = rand.nextInt(matingDNA.get(0).size());
        List<List<Integer>> result = new ArrayList<>();
        List<Integer> firstMatingDNA = matingDNA.get(0);
        List<Integer> secondMatingDNA = matingDNA.get(1);
        List<Integer> firstPart = firstMatingDNA.subList(0, firstMatingDNA.size()-pivot);
        List<Integer> secondPart = secondMatingDNA.subList(secondMatingDNA.size()-pivot, secondMatingDNA.size());
        ArrayList<Integer> firstResult = new ArrayList<>(firstPart);
        firstResult.addAll(secondPart);
        result.add(firstResult);

        List<Integer> firstPartList2 = secondMatingDNA.subList(0, secondMatingDNA.size()-pivot);
        List<Integer> secondPartList2 = firstMatingDNA.subList(firstMatingDNA.size()-pivot, firstMatingDNA.size());
        ArrayList<Integer> secondResult = new ArrayList<>(firstPartList2);
        secondResult.addAll(secondPartList2);
        result.add(secondResult);
        return result;
    }

    /*
     * Randomly mutates a piece of DNA
     */
    private List<Integer> mutate(List<Integer> DNA) {
        List<Integer> mutateDNA = new ArrayList<>(DNA);
        int chosenBaseIndex = rand.nextInt(mutateDNA.size());
        int chosenBaseValue = Utils.getRandomInt(Individual.INDIVIDUAL_BASE_TYPES);
        mutateDNA.set(chosenBaseIndex, chosenBaseValue);
        return mutateDNA;
    }

    public int getPopulationSize() {return this.populationSize;}

    public double getBestFitness() {return this.bestFitness;}

    public void setBestFitness(double bestFitness) {this.bestFitness = bestFitness;}
}
