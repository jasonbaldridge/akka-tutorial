import akka.actor._

object TickTock {
  
  object Tick
  object Tock

  val random = new scala.util.Random

  class Counter extends Actor {
    var counter = 0

    def receive = {
      case Tick => 
	Thread.sleep(random.nextInt(500))
	counter += 1
        println(self.path.name + " after Tick: " + counter)

      case Tock =>
	Thread.sleep(random.nextInt(500))
	counter -= 1
        println(self.path.name + " after Tock: " + counter)

    }
  }

  def main (args: Array[String]) {

    val system = ActorSystem("CountingDemo")

    println("Starting Counter 1.")
    val counter1 = system.actorOf(Props[Counter], "Counter1")

    counter1 ! Tick

    (1 to 5).foreach(_ => counter1 ! Tick)

    Thread.sleep(1000)
    println("Starting Counter 2.")
    val counter2 = system.actorOf(Props[Counter], "Counter2")

    (1 to 5).foreach { _ =>
      counter1 ! Tock
      counter2 ! Tick
    }
 
    Thread.sleep(2000)
    system.shutdown
  }

}


