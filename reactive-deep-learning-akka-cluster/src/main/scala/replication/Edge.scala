package replication

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.Cluster
import akka.contrib.datareplication.{ORSet, DataReplication}
import akka.contrib.datareplication.Replicator.{Changed, Subscribe, WriteLocal, Update}
import replication.Edge.{AddInput, AddOutput, UpdateWeight}
import replication.Node.{Ack, Input, WeightedInput}

object Edge {
  case class AddInput(input: ActorRef)
  case class AddOutput(output: ActorRef)
  case class UpdateWeight(weight: Double)

  def props(): Props = Props[Edge]
}

trait HasInput extends Actor {
  var input: ActorRef = _
  def addInput(): Receive = {
    case AddInput(i) =>
      input = i
      sender() ! Ack
  }
}

trait HasOutput extends Actor {
  var output: ActorRef = _
  def addOutput(): Receive = {
    case AddOutput(o) =>
      output = o
      sender() ! Ack
  }
}

class Edge extends HasInput with HasOutput {
  var weight: Double = 0.3

  val replicator = DataReplication(context.system).replicator
  implicit val cluster = Cluster(context.system)

  replicator ! Subscribe("key", self)

  override def receive: Receive = run orElse addInput orElse addOutput

  def run: Receive = {
    case Input(f) =>
      output ! WeightedInput(f, weight)

    case UpdateWeight(w) =>
      println(s"Updating weight to $w")
      replicator ! Update("key", ORSet(), WriteLocal)(_ + 1)
      weight = w

    case Changed("key", ORSet(elements)) =>
      println("Current elements: {}", elements)
  }
}
