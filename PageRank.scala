import akka.actor._
import scala.concurrent.{Future,Await}
import akka.pattern.ask
import akka.util._
import scala.concurrent.duration._

/**
 * An implementation of PageRank using Akka actors.
 *
 * For more information regarding PageRank, see Chapter 21 of
 * Manning, Raghavan, and Schutze (free online):
 *   http://nlp.stanford.edu/IR-book/
 *
 * Also, see Jimmy Lin's PageRank exercise:
 *   http://lintool.github.com/Cloud9/docs/exercises/pagerank.html
 *
 * Jimmy's example graph data is available in the directory
 *   akka-tutorial/data/pagerank
 *
 * To try the code out, run it like this (in the akka-tutorial dir):
 *
 *   $ ./build
 *   > run-main PageRank data/pagerank/sample-small.txt
 *
 * There are lots of ways this could be improved to either make it
 * into more idiomatic Akka and perhaps do less blocking than I
 * ended up using to control what happens on each iteration.
 * 
 * @author Jason Baldridge
 */
object PageRank {

  // The messages to and between vertices
  case class InitializeVertex(mass: Double, neighbors: List[ActorRef])
  object SpreadMass
  case class TakeMass(mass: Double)
  case class Update(jumpTimesUniform: Double, oneMinusJumpFactor: Double, mass: Double)
  object GetPageRank

  implicit val timeout = Timeout(10 seconds)

  /**
   * Entry point into actor-based PageRank: create the actors for the
   * graph and its vertices and start the work.
   */
  def main (args: Array[String]) {

    val inputGraphFile = args(0)
    val jumpFactor = if (args.length > 1) args(1).toDouble else .15
    val maxIters = 50
    val diffTolerance = 1E-6

    // Construct the graph from the source file
    val graph: List[(String, List[String])] = 
      io.Source.fromFile(inputGraphFile).getLines
	.map(_.split("\\t").toList)
	.map(vertices => (vertices.head, vertices.tail))
	.toList
    
    // Precompute some values that will be used often for the updates.
    val numVertices = graph.length
    val uniformProbability = 1.0/numVertices
    val jumpTimesUniform = jumpFactor/numVertices
    val oneMinusJumpFactor = 1.0-jumpFactor

    val system = ActorSystem("PageRank")
    import system.dispatcher

    // Create the vertex actors, and put in a map so we can
    // get them by ID.
    val idsToActors = graph.map {
      case(vertexId, _) => 
	val vertex = system.actorOf(Props[Vertex], vertexId)
        (vertexId, vertex)
    } toMap

    // Have each vertex set its actor neighbors
    // Should do Future.traverse(vertices)(...)
    val readyFutures: Seq[Future[Boolean]] = 
      for ((vertexId, adjacencyList) <- graph) yield {
	val vertex = idsToActors(vertexId) 
	val neighbors = adjacencyList.map(idsToActors)
	(vertex ? InitializeVertex(uniformProbability, neighbors)).mapTo[Boolean]
      }
    
    // All vertices must be ready to go before we kick off the 
    // algorithm. There is surely a better way to set all this up.
    val allReadyFuture: Future[Boolean] = 
      Future.reduce(readyFutures)(_ && _)

    val ready = Await.result(allReadyFuture, 10 seconds)
    
    // The list of vertex actors, used for dispatching messages to all.
    val vertices = idsToActors.values.toList
    
    var done = false
    var currentIteration = 1

    while (!done) {

      // Tell all vertices to spread their mass and get back the
      // missing mass.
      val missingMassFuture: Future[Double] = 
	Future.traverse(vertices) { 
	  v=> (v ? SpreadMass).mapTo[Double]
	}.map(_.sum)
    
      val totalMissingMass = Await.result(missingMassFuture, 10 seconds)
      val eachVertexRedistributedMass = totalMissingMass / numVertices
    
      // Tell all vertices to update their pagerank values.
      val updateValues = 
	Update(jumpTimesUniform, 
               oneMinusJumpFactor, 
               eachVertexRedistributedMass)

      val diffsFuture: Future[Seq[Double]] = 
	Future.traverse(vertices) {
	  v => (v ? updateValues).mapTo[Double]
	}
	
      val averageDiff = 
	Await.result(diffsFuture.map(_.sum/numVertices), 10 seconds)

      println("Iteration " + currentIteration 
	      + ": average diff == " + averageDiff)
    
      currentIteration += 1
    
      if (currentIteration > maxIters || averageDiff < diffTolerance) {
        done = true
    
        // Output ten highest ranked vertices
	val pagerankFutures: Future[Seq[(String,Double)]] = 
	  Future.traverse(vertices) {
	    v => (v ? GetPageRank).mapTo[(String,Double)]
	  }

	pagerankFutures.foreach { pageranks => {
	  val topVertices = pageranks.sortBy(-_._2).take(10)
	  for ((id, pagerank) <- topVertices) 
	    println(pagerank + "\t" + id)
        }}
      }
    }
    system.shutdown
  }

  /**
   * An actor for a vertex in the graph.
   */
  class Vertex extends Actor {

    var neighbors: List[ActorRef] = List[ActorRef]()
    var pagerank = 0.0
    var outdegree = 0.0
    var receivedMass = 0.0
    val id = self.path.name

    def receive = {

      // Initialize this vertex
      case InitializeVertex (mass, neighborActorRefs) => 
	pagerank = mass
        neighbors = neighborActorRefs
	outdegree = neighbors.length
	sender ! true

      // Send pagerank mass to neighbors
      case SpreadMass =>
        if (outdegree == 0) {
          sender ! pagerank
        } else {
          val amountPerNeighbor = pagerank / outdegree
          neighbors.foreach(_ ! TakeMass(amountPerNeighbor))
          sender ! 0.0
        }

      // Accumulate mass received from neighbors
      case TakeMass(contribution) =>
        receivedMass += contribution

      // Return the id of the node and its current pagerank value
      case GetPageRank =>
	sender ! (id,pagerank)

      // Update pagerank value based on received mass, jump factor,
      // and redistributed mass from dangling nodes.
      case Update(jumpTimesUniform, oneMinusJumpFactor, redistributedMass) =>
        val updatedPagerank = 
          jumpTimesUniform + oneMinusJumpFactor*(redistributedMass + receivedMass)
        val diff = math.abs(pagerank-updatedPagerank)
        pagerank = updatedPagerank
        receivedMass = 0.0
        sender ! diff

    }
  }

}


