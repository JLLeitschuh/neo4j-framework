/*
 * Copyright (c) 2014 GraphAware
 *
 * This file is part of GraphAware.
 *
 * GraphAware is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of
 * the GNU General Public License along with this program.  If not, see
 * <http://www.gnu.org/licenses/>.
 */

/**
 * EXPERIMENTAL
 */
package com.graphaware.generator;

import com.graphaware.common.util.UnorderedPair;
import org.neo4j.graphdb.*;

import java.util.ArrayList;
import java.util.Collections;

import static com.graphaware.common.util.IterableUtils.getSingleOrNull;
import static java.lang.Math.min;
import static java.util.Collections.sort;

/**
 * A simple minded generator of graphs based on a degree distribution. So far,
 * the model accepts a list of node degrees as an input and creates a graph
 * according to that.
 * <p/>
 * Also, the distribution of randomly generated graphs isn't exactly uniform
 * (see the paper below)
 * <p/>
 * Uses Blitzstein-Diaconis algorithm Ref:
 * <p/>
 * A SEQUENTIAL IMPORTANCE SAMPLING ALGORITHM FOR GENERATING RANDOM GRAPHS WITH
 * PRESCRIBED DEGREES By Joseph Blitzstein and Persi Diaconis (Stanford
 * University). (Harvard, June 2006)
 *
 * @author Vojtech Havlicek (Graphaware)
 */
public class Simple implements ConfigurationModelGenerator {

    private final GraphDatabaseService database;

    /**
     * @param database
     */
    public Simple(GraphDatabaseService database) {
        this.database = database;
    }

    /**
     * Implements a simple Havel-Hakimi randomized generator as described in
     * Blitzstein/Diaconis
     *
     * @param distribution
     * @return
     */
    public void generateGraph(ArrayList<Integer> distribution) {
        ArrayList<UnorderedPair<Integer>> edges = getEdges(distribution);

        queryDB(edges);
    }

    /**
     * Returns an edge-set corresponding to a randomly chosen simple graph.
     *
     * @param distribution
     * @return
     */
    private ArrayList<UnorderedPair<Integer>> getEdges(ArrayList<Integer> distribution) throws InvalidDistributionException {
        if (!isValidDistribution(distribution)) {
            throw new InvalidDistributionException("The supplied distribution is not graphical");
        }

        // If the dstribution is graphical => exist a sub-distribution which is graphical
        ArrayList<UnorderedPair<Integer>> edges = new ArrayList<>();

        while (!isZeroArrayList(distribution)) {
            int length = distribution.size();
            int index = 0;
            int min = Integer.MAX_VALUE;

            // find minimal nonzero element
            for (int i = 0; i < distribution.size(); ++i) {
                int elem = distribution.get(i);
                if (elem != 0 && elem < min) {
                    min = elem;
                    index = i;
                }
            }

            // Obtain a candidate list:
            while (true) {
                ArrayList<Integer> temp = new ArrayList<>(distribution);

                // TODO : this should be proportional to degree to 
                // make the random graph distribution as uniform as possible
                int rnd = (int) Math.floor(Math.random() * (length - 1)); // choose an index from one elem. less range. OK
                int candidateIndex = rnd >= index ? rnd + 1 : rnd;       // skip index. OK

                UnorderedPair<Integer> edgeCandidate = new UnorderedPair<>(candidateIndex, index);

                /**
                 * Improve this one, check if edge has already been added.
                 */
                boolean skip = false;
                for (UnorderedPair<Integer> edge : edges) {
                    if (edge.equals(edgeCandidate)) {
                        skip = true;
                        break;
                    }
                }

                if (skip) {
                    continue;
                }

                /**
                 * Prepare the candidate set and test if it is graphical
                 */
                decrease(temp, index);
                decrease(temp, candidateIndex);

                if (isValidDistribution(temp)) { // use Erdos-Galai test, since it doesn't sort the entries
                    distribution = temp;              // assign temp to distribution
                    edges.add(edgeCandidate);         // edge is allowed, add it.
                    break;
                }
            }
        }
        return edges;
    }

    /**
     * Returns true if the supplied distribution is a zero-list.
     *
     * @param distribution
     * @return boolean is th supplied distribution a zero-list?
     */
    private boolean isZeroArrayList(ArrayList<Integer> distribution) {
        for (int degree : distribution) {
            if (degree > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reduces element value by 1
     *
     * @param distribution
     * @param index
     */
    private void decrease(ArrayList<Integer> distribution, int index) {
        distribution.set(index, distribution.get(index) - 1);
    }

    /**
     * All valid distributions must be graphical. This is tested using
     * Erdos-Gallai condition on degree distribution graphicality. (see
     * Blitzstein-Diaconis paper)
     * <p/>
     * Warning! Sorts the distrib.
     *
     * @param distribution
     * @return
     */
    public boolean isValidDistribution(ArrayList<Integer> distribution) {
        // Do this in-place instead?
        ArrayList<Integer> copy = new ArrayList<>(distribution);

        int L = copy.size();
        int degreeSum = 0;           // Has to be even by the handshaking lemma

        for (int degree : copy) {
            if (degree < 0) {
                return false;
            }
            degreeSum += degree;
        }

        if (degreeSum % 2 != 0) {
            return false;
        }

        sort(copy, Collections.reverseOrder());
        // Erdos-Gallai test
        for (int k = 1; k < L; ++k) {
            int sum = 0;
            for (int i = 0; i < k; ++i) {
                sum += copy.get(i);
            }

            int comp = 0;
            for (int j = k; j < L; ++j) {
                comp += min(k, copy.get(j));
            }

            if (sum > k * (k - 1) + comp) {
                return false;
            }
        }

        return true;
    }

    /**
     * Use Havel-Hakimi test instead of the Erdos-Gallai condition TODO: Do
     * these in-place?
     *
     * @param distribution
     * @param havelHakimi
     * @return
     */
    private boolean isValidDistribution(ArrayList<Integer> distribution, boolean havelHakimi) {
        /* 
         * The test fails if there are less available 
         * nodes to connect to than the degree of lar-
         * gest node.
         */

        ArrayList<Integer> copy = new ArrayList<>(distribution);

        int i = 0, L = 0, first = 0;

        while (L > 0) {   //todo this can't work
            first = copy.get(i);
            L--;

            int j = 1;
            for (int k = 0; k < first; ++k) {
                while (copy.get(j) == 0) {

                    j++;
                    if (j > L) {
                        return false;
                    }
                }

                copy.set(j, copy.get(j) - 1);
            }

            copy.set(i, 0);
            sort(copy, Collections.reverseOrder());
        }
        return true;
    }

    /**
     * Sends a mutating "query" to the db, constructing the requested simple
     * graph.
     * <p/>
     * TODO: make sure undirected nodes are created
     *
     * @param edges
     */
    private void queryDB(ArrayList<UnorderedPair<Integer>> edges) {

        /* Now feed the pairs to Neo4j -  I am using a 
         graph generating code from the test written by
         Adam George (Graphaware) to do this. Please let me know
         if that is ok */
        try (Transaction transaction = this.database.beginTx()) {
            Label personLabel = DynamicLabel.label("Node");
            RelationshipType relationshipType = DynamicRelationshipType.withName("relto");

            for (UnorderedPair<Integer> edge : edges) {
                Node node = findOrCreateNode(personLabel, "" + edge.first());
                Node friend = findOrCreateNode(personLabel, "" + edge.second());
                node.createRelationshipTo(friend, relationshipType);
            }

            transaction.success();
        }
    }

    private Node findOrCreateNode(Label label, String name) {
        Node existing = getSingleOrNull(database.findNodesByLabelAndProperty(label, "name", name));
        if (existing != null) {
            return existing;
        }

        Node newNode = database.createNode(label);
        newNode.setProperty("name", name);
        return newNode;
    }
}
