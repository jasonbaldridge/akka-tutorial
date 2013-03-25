import scala.concurrent.{Future,ExecutionContext,Await}
import ExecutionContext.Implicits.global
import scala.concurrent.duration._

object BasicFuture extends App {

  // Create a Future Int. Not practically useful here, but 
  // it keeps it simple.
  val aFuture: Future[Int] = Future(42)

  // Now let's perform compute a new Future value based on 
  // the value in that Future. We get this by using map on 
  // aFuture (basically, Future is a monad; or, just think 
  // of a Future as being like a Seq with just one element).
  val a2Future: Future[Int] = aFuture.map(_*2 + 1900)

  // To get the actual value out, we Await the result of 
  // the Future. These operations were actually not being 
  // carried out sequentially, though we can't tell that
  // because of how little is being done.
  val a2Val: Int = Await.result(a2Future, 1.second)
  println("The future is/was " + a2Val + "?")

  // Let's get a couple more Future values.
  val bFuture: Future[Double] = Future(3.14)
  val cFuture: Future[Double] = Future(10.0)

  // Again, because Futures are monads, we can use them 
  // in for-comprehensions. E.g., here's dividing the value
  // of one by the other, which still returns a Future.
  val divFuture: Future[Double] = for {
    bVal <- bFuture
    cVal <- cFuture
  } yield bVal/cVal

  // Again, the above happened asyncronously (again, 
  // uselessly).
  val divVal: Double = Await.result(divFuture, 1.second)
  println("B/C = " + divVal)

}
