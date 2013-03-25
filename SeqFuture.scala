import scala.concurrent.{Future,ExecutionContext,Await}
import ExecutionContext.Implicits.global
import scala.concurrent.duration._

object SeqFuture extends App {

  // Val the number of items to play with while trying out 
  // Sequences of Futures.
  val size = if (args.length==1) args(0).toInt else 10

  // Get a sequence of Futures of squared values
  def getFutureSquares(num: Int) = (1 to num).map(x=>Future(x*x))

  // A function that takes it's time to respond.
  def sleepyFnc(x: Int) = {
    Thread.sleep(10) // Yawn
    (x+42)/(x+3.14)
  }
  
  /**
   * Our first try: get some futures, but then get their
   * values right away, thereby blocking and going sequential
   * prematurely. (This is what *not* to do.)
   */

  // Sequence of future squared values
  val squareFutures1: Seq[Future[Int]] = getFutureSquares(size)

  // Let's do the bad thing and wait for each result (blocking).
  val t1 = System.currentTimeMillis
  val squares: Seq[Int] = squareFutures1.map { sf =>
    Await.result(sf, 1 second)
					    }

  // Now use sleepyFnc sequentially and get the sum
  val divSum = squares.map(sleepyFnc).sum
  val t2 = System.currentTimeMillis
  println
  println("Blocking sum: " + divSum)
  println(" It took " + (t2-t1) + " milliseconds")

  // Get another sequence of future squares
  val squareFutures2: Seq[Future[Int]] = getFutureSquares(size)
  
  val t3 = System.currentTimeMillis

  // Map sleepyFnc over the futures asynchronously
  val futureSleepy2: Seq[Future[Double]] = 
    squareFutures2.map(_.map(sleepyFnc))
  
  // Get the sum, asynchronously
  val futureDivSum = Future.reduce(futureSleepy2)(_+_)

  // Block at the end to get the result
  val divSum2 = Await.result(futureDivSum, 20 second)
  val t4 = System.currentTimeMillis
  println
  println("Non-blocking sum: "+ divSum2)
  println("It took " + (t4-t3) + " milliseconds")

  // A second way to do the same w/o blocking.

  // Get another sequence of future squares
  val squareFutures3: Seq[Future[Int]] = getFutureSquares(size)

  val t5 = System.currentTimeMillis

  // Use the traverse method to apply a function to each element
  // of a sequency of futures to get a Future Seq. Not especially
  // useful here, but great if you want to keep the Seq around
  // for further use.
  val futureSleepy3: Future[Seq[Double]] = 
    Future.traverse(squareFutures3)(_.map(sleepyFnc))
  
  // Okay, so let's go ahead and get the sum.
  val futureSum3 = futureSleepy3.map(_.sum)

  val divSum3 = Await.result(futureSum3, 20 second)
  val t6 = System.currentTimeMillis
  println
  println("Non-blocking sum 2: "+ divSum3)
  println(" It took " + (t6-t5) + " milliseconds")
  println

}

