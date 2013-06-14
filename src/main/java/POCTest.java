import java.io.File;
import java.util.Comparator;
import java.util.Iterator;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.neo4j.graphalgo.CostEvaluator;
import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphalgo.WeightedPath;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.graphdb.traversal.Traverser;
import org.neo4j.kernel.Traversal;

public class POCTest {

	private static GraphDatabaseService graphDb;
	private Transaction tx;

	private static enum ExampleTypes implements RelationshipType {
		TRIP, HOSTING;
	}

	private static enum NodeTypes implements RelationshipType {
		CITY, HOTEL;
	}

	@BeforeClass
	public static void startDb() {
		String storeDir = "/tmp/graphDir";
		deleteFileOrDirectory(new File(storeDir));
		graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(storeDir);
	}

	@Before
	public void doBefore() {
		tx = graphDb.beginTx();
	}

	@After
	public void doAfter() {
		tx.success();
		tx.finish();
	}

	@AfterClass
	public static void shutdownDb() {
		try {
			if (graphDb != null)
				graphDb.shutdown();
		} finally {
			graphDb = null;
		}
	}

	@Test
	public void dijkstraUsage() {
		// Cidades
		Node campinas = createCity("Campinas");
		Node saoPaulo = createCity("Sao Paulo");
		Node caraguatatuba = createCity("Caraguatatuba");
		Node santos = createCity("Santos");

		createTrip(campinas, saoPaulo, 0.4, 1.0, 830.0, "Voo 3742");
		createTrip(campinas, santos, 2.5, 0.3, 130.0, "Linha 371");
		createTrip(campinas, saoPaulo, 1.5, 0.4, 40.0, "Linha 372");
		createTrip(saoPaulo, santos, 0.7, 0.4, 50.0, "Linha 373");
		createTrip(saoPaulo, caraguatatuba, 2.1, 0.3, 150.0, "Linha 374");

		// Hoteis
		createHotel(saoPaulo, "H1", false, false, 150.0, 0.6, 0.0);
		createHotel(saoPaulo, "H2", true, false, 350.0, 0.9, 1.0);

		createHotel(santos, "H3", false, true, 25.0, 0.1, 0.0);
		createHotel(santos, "H4", true, true, 125.0, 0.5, 0.4);

		createHotel(caraguatatuba, "H5", true, true, 225.0, 0.7, 0.6);
		createHotel(caraguatatuba, "H6", true, true, 270.0, 0.9, 1.0);

		// findCheapestPathsWithDijkstra(campinas, h5, 1000.0, true, true);
		traverse(campinas, 1000.0, true, true);
	}

	private Node createHotel(Node city, String name, boolean hasChildren, boolean hasBeach, double price,
			double confort, double children) {
		Node hotel = graphDb.createNode();
		hotel.setProperty("children", hasChildren);
		hotel.setProperty("beach", hasBeach);
		hotel.setProperty("name", name);
		hotel.setProperty("type", NodeTypes.HOTEL.name());

		Relationship hosting = city.createRelationshipTo(hotel, ExampleTypes.HOSTING);
		hosting.setProperty("price", price);
		hosting.setProperty("confort", confort);
		hosting.setProperty("children", children);
		hosting.setProperty("name", "Hosting on " + name);

		return hotel;
	}

	private Node createCity(String name) {
		Node city = graphDb.createNode();
		city.setProperty("name", name);
		city.setProperty("type", NodeTypes.CITY.name());
		return city;
	}

	private void createTrip(Node start, Node end, double time, double confort, double price, String name) {
		Relationship relationship = start.createRelationshipTo(end, ExampleTypes.TRIP);
		relationship.setProperty("time", time);
		relationship.setProperty("confort", confort);
		relationship.setProperty("price", price);
		relationship.setProperty("name", name);
	}

	public void traverse(final Node start, final double costRestriction, final boolean hasChildren,
			final boolean hasBeach) {
		TraversalDescription td = Traversal.description().breadthFirst()
				.relationships(ExampleTypes.TRIP, Direction.OUTGOING)
				.relationships(ExampleTypes.HOSTING, Direction.OUTGOING).evaluator(new Evaluator() {

					@Override
					public Evaluation evaluate(Path path) {
						Node end = path.endNode();
						NodeTypes type = NodeTypes.valueOf((String) end.getProperty("type"));

						if (type == NodeTypes.CITY) {
							return Evaluation.EXCLUDE_AND_CONTINUE;
						} else {
							if (((Boolean) path.endNode().getProperty("children")) == hasChildren
									&& ((Boolean) path.endNode().getProperty("beach")) == hasBeach) {
								return Evaluation.INCLUDE_AND_CONTINUE;
							} else {
								return Evaluation.EXCLUDE_AND_CONTINUE;
							}
						}
					}
				}).sort(new Comparator<Path>() {

					@Override
					public int compare(Path o1, Path o2) {
						return getCost(o1).compareTo(getCost(o2));
					}

					public Double getCost(Path path) {
						double cost = 0.0;
						for (Relationship relationship : path.relationships()) {
							cost += getCost(relationship);
						}
						return cost;
					}

					public Double getCost(Relationship relationship) {
						if (relationship.getType().equals(ExampleTypes.TRIP)) {
							double time = (Double) relationship.getProperty("time");
							double confort = (Double) relationship.getProperty("confort");
							double price = (Double) relationship.getProperty("price");

							if (hasChildren) {
								return 2 * time - 3 * confort + price / 1000;
							} else {
								return 3 * time - 2 * confort + price / 1000;
							}
						} else if (relationship.getType().equals(ExampleTypes.HOSTING)) {
							double children = (Double) relationship.getProperty("children");
							double confort = (Double) relationship.getProperty("confort");
							double price = (Double) relationship.getProperty("price");

							if (hasChildren) {
								return -5 * children - 3 * confort + price / 1000;
							} else {
								return -2 * confort + price / 1000;
							}
						}
						throw new RuntimeException();
					}
				});
		Traverser t = td.traverse(start);
		Iterator<Path> it = t.iterator();
		while (it.hasNext()) {
			Path path = it.next();
			System.out.println(path.startNode().getProperty("name"));

			for (Relationship relationship : path.relationships()) {
				System.out.println("\t" + relationship.getType() + " - " + relationship.getProperty("name"));
			}

			System.out.println(path.endNode().getProperty("name"));
		}
	}

	public void findCheapestPathsWithDijkstra(final Node nodeA, final Node nodeB, final double costRestriction,
			final boolean hasChildren, final boolean hasBeach) {
		PathFinder<WeightedPath> finder = GraphAlgoFactory.dijkstra(Traversal.expanderForAllTypes(Direction.BOTH),
				new CostEvaluator<Double>() {

					@Override
					public Double getCost(Relationship relationship, Direction direction) {
						if (relationship.getType().equals(ExampleTypes.TRIP)) {
							double time = (Double) relationship.getProperty("time");
							double confort = (Double) relationship.getProperty("confort");
							double price = (Double) relationship.getProperty("price");

							if (hasChildren) {
								return 2 * time - 3 * confort + price;
							} else {
								return 3 * time - 2 * confort + price;
							}
						} else if (relationship.getType().equals(ExampleTypes.HOSTING)) {
							double children = (Double) relationship.getProperty("children");
							double confort = (Double) relationship.getProperty("confort");
							double price = (Double) relationship.getProperty("price");

							if (hasChildren) {
								return -5 * children - 3 * confort + price;
							} else {
								return -2 * confort + price;
							}
						}
						throw new RuntimeException();
					}
				});

		Iterable<WeightedPath> paths = finder.findAllPaths(nodeA, nodeB);

		for (WeightedPath path : paths) {
			System.out.println(path.startNode().getProperty("name") + " - " + path.weight());

			for (Relationship relationship : path.relationships()) {
				System.out.println("\t" + relationship.getType() + " - " + relationship.getProperty("name"));
			}

			System.out.println(path.endNode().getProperty("name"));
		}
	}

	public static void deleteFileOrDirectory(File file) {
		if (!file.exists()) {
			return;
		}

		if (file.isDirectory()) {
			for (File child : file.listFiles()) {
				deleteFileOrDirectory(child);
			}
		} else {
			file.delete();
		}
	}
}
