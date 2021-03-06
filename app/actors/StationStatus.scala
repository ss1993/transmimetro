package actors

import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import messages._
import models.Passenger

import scala.concurrent.duration._
import scala.util.Random


class StationStatus extends Actor with ActorLogging {

  var stationsStatus = Map.empty[String, List[Passenger]]
  var passengersInLogger: ActorRef = _

  implicit val executionContext = context.dispatcher
  val tick = context.system.scheduler.schedule(0 millis, 5 seconds, self, Tick)

  override def preStart(): Unit = {
    log.info("Starting StationStatus")
    passengersInLogger = context.actorOf(Props[PassengersInLogger], name = "passengersInLogger")
    loadStations()
  }

  override def postStop(): Unit = tick.cancel()

  def receive: Receive = {
    case Tick =>
      log.info("Updating incoming passengers")
      updateIncomingPassengers()

    case FetchStationStatus =>
      log.info("Fetch received")
      sender() ! StationsStatusUpdated(stationsStatus)

    case UpdateBoardedPassengers(boardedPassengers) =>
      boardedPassengers.foreach { case (station, nPassengers) =>
        stationsStatus = stationsStatus.updated(station, stationsStatus(station).drop(nPassengers))
      }
      sender() ! StationsCapacitiesUpdated(stationsStatus.map {case (station, passengers) =>
        (station, passengers.size)
      })
      log.info("Stations status updated with boarded passengers {}", stationsStatus.mapValues(_.size))
  }

  def updateIncomingPassengers(): Unit = {
    val now = LocalTime.now()
      .truncatedTo(ChronoUnit.MINUTES)
      .format(DateTimeFormatter.ofPattern("HHmm"))

    stationsStatus.foreach { case (station, passengers) =>

      val destinations = stationsStatus.keys.toArray

      val incomingPassengers = (0 to Random.nextInt(3)).map { _ =>
        val randomDestinationIndex = Random.nextInt(stationsStatus.size)
        Passenger(now, destinations(randomDestinationIndex))
      }.toList

      passengersInLogger ! LogPassengersIn(station, incomingPassengers)
      stationsStatus = stationsStatus.updated(station, passengers ++: incomingPassengers)
    }
    log.info("Incoming passengers updated {}", stationsStatus.mapValues{_.size})
  }


  def loadStations(): Unit = {

    val file = new File("schedules.csv")

    val stations = for (
      line: String <- scala.io.Source.fromFile(file).getLines().toList.drop(1)
    ) yield line.split(';')(1)

    stationsStatus = Map(
      stations
      .distinct
      .map { station =>
        (station, List.empty[Passenger])
      }: _*)

  }

}
