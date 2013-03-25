import akka.actor._

object PingPong {
  
  val random = new scala.util.Random

  case class SetPartner(partner: ActorRef)
  case class Ball(volley: Int)

  class Player extends Actor {

    var partner = context.system.deadLetters

    def receive = {

      case SetPartner(p: ActorRef) => 
	partner = p

      case Ball(volley) =>
	Thread.sleep(500)
	
	if (random.nextInt(100) < volley) {
	  println(self.path.name + ": I dropped the ball... Sorry!")
	} else {
	  println (self.path.name + ": Volley number " + volley)
	  partner ! Ball(volley+1)
	}
    }
  }

  def main (args: Array[String]) {
    val system = ActorSystem("PingPongDemo")
    val ping = system.actorOf(Props[Player], "Ping")
    val pong = system.actorOf(Props[Player], "Pong")
    
    ping ! SetPartner(pong)
    pong ! SetPartner(ping)

    pong ! Ball(1)

    Thread.sleep(5000)
    println("Times up!")
    system.shutdown
  }

}


