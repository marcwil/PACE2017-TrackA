/*
 * Copyright (c) 2017, Hisao Tamaki
 */

package tw.exact;

import java.util.ArrayList;
import java.util.BitSet;

public class MainDecomposer {
    private static boolean VERBOSE = false;
    //  private static boolean VERBOSE = true;
    private static boolean DEBUG = false;
//private static boolean debug = true;

    private static long time0;

    public static TreeDecomposition decompose(Graph g) {
        log("decompose n = " + g.n);
        if (g.n == 0) {
            TreeDecomposition td = new TreeDecomposition(0, -1, g);
            return td;
        }

        ArrayList<XBitSet> components = g.getComponents(new XBitSet());
        if (System.getenv("debug") != null) {
            System.err.println("Number of components " + components.size());
            components.stream().map(BitSet::cardinality).forEach(c -> System.err.print(c + ", "));
            System.err.println();
        }

        int nc = components.size();
        if (nc == 1) {
            return decomposeConnected(g);
        }

        int invs[][] = new int[nc][];
        Graph graphs[] = new Graph[nc];

        for (int i = 0; i < nc; i++) {
            XBitSet compo = components.get(i);
            int nv = compo.cardinality();
            graphs[i] = new Graph(nv);
            invs[i] = new int[nv];
            int conv[] = new int[g.n];
            int k = 0;
            for (int v = 0; v < g.n; v++) {
                if (compo.get(v)) {
                    conv[v] = k;
                    invs[i][k] = v;
                    k++;
                } else {
                    conv[v] = -1;
                }
            }
            graphs[i].inheritEdges(g, conv, invs[i]);
        }

        TreeDecomposition td = new TreeDecomposition(0, 0, g);

        for (int i = 0; i < nc; i++) {
            TreeDecomposition td1 = decomposeConnected(graphs[i]);
            if (td1 == null) {
                return null;
            }
            td.combineWith(td1, invs[i], null);
        }
        return td;
    }

    public static TreeDecomposition decomposeConnected(Graph g) {
        log("decomposeConnected: n = " + g.n);
        if (System.getenv("debug") != null) {
            System.err.println("Starting greedy decomposition of " + g.n + " node connected component");
        }

        if (g.n <= 2) {
            TreeDecomposition td = new TreeDecomposition(0, g.n - 1, g);
            td.addBag(g.all.toArray());
            return td;
        }

        Bag best = null;

        GreedyDecomposer.Mode[] modes =
                new GreedyDecomposer.Mode[]{
                        GreedyDecomposer.Mode.fill,
                        GreedyDecomposer.Mode.defect,
                        GreedyDecomposer.Mode.degree
                };

        for (GreedyDecomposer.Mode mode : modes) {
            Bag whole = new Bag(g);

            GreedyDecomposer mfd = new GreedyDecomposer(whole, mode);
//      GreedyDecomposer mfd = new GreedyDecomposer(whole);

            mfd.decompose();

            log("greedy decomposition (" + mode + ") obtained with " +
                    whole.nestedBags.size() + " bags and width " +
                    whole.width);
            if (System.getenv("debug") != null) {
                System.err.println("greedy decomposition (" + mode + ") obtained with " +
                        whole.nestedBags.size() + " bags and width " +
                        whole.width);
            }

            whole.detectSafeSeparators();

            if (System.getenv("debug") != null) {
                int clique_safe_seps = 0;
                for (Separator s : whole.separators) {
                    XBitSet vertices = s.vertexSet;
                    boolean realclique = true;
                    for (int i: vertices.toArray()) {
                        for (int j: vertices.toArray()) {
                            if (i == j)
                                continue;
                            if (!g.areAdjacent(i,j))
                                realclique = false;
                        }
                    }
                    if (realclique)
                        clique_safe_seps += 1;
                }
                System.err.println(whole.countSafeSeparators() + " safe separators (including " + clique_safe_seps + "cliques) found in graph with n=" + g.n);
            }

            whole.validate();

            whole.pack();

            whole.validate();

            if (System.getenv("debug") != null) {
                System.err.println("the decomposition packed into " +
                        whole.nestedBags.size() + " bags, separatorWidth = " +
                        whole.separatorWidth + ", max bag size = " +
                        whole.maxNestedBagSize());
            }
            log("the decomposition packed into " +
                    whole.nestedBags.size() + " bags, separatorWidth = " +
                    whole.separatorWidth + ", max bag size = " +
                    whole.maxNestedBagSize());

            if (best == null ||
                    whole.maxNestedBagSize() < best.maxNestedBagSize()) {
                best = whole;
            }
        }
//    best = whole;

        //    whole.dump();

        int lowestPossible = best.separatorWidth;

        if (System.getenv("debug") != null) {
            System.err.println("\n#####\nStarting IO Decomposition of " + g.n + " node component");
        }

        for (Bag bag : best.nestedBags) {
            if (bag.getWidth() > lowestPossible) {
                bag.makeRefinable();
                if (System.getenv("debug") != null) {
                    System.err.println("$$$$$$$$$ Calling IO Decomp on " + bag.graph.n + " node safely separated component.");
                }
                IODecomposer mtd = new IODecomposer(bag, g.minDegree(), g.n - 1);
                mtd.decompose();
                int w = bag.getWidth();
                if (w > lowestPossible) {
                    lowestPossible = w;
                }
            }
        }
        if (System.getenv("debug") != null) {
            System.err.println("Finished IO Decomposition of " + g.n + " node component\n#####\n");
        }

        log("flattening");

        best.flatten();

        log("the decomposition flattened into " +
                best.nestedBags.size() + " bags");

//    whole.dump();

        return best.toTreeDecomposition();
    }

    static void log(String message) {
        if (VERBOSE) {
            System.out.println(message);
        }
    }

    public static void main(String[] args) {
        Graph g = Graph.readGraph(System.in);
        TreeDecomposition td = decompose(g);
        td.writeTo(System.out);
    }
}
