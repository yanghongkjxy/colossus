package colossus.examples


import java.util.Date
import java.text.SimpleDateFormat

import colossus._
import colossus.core.{Initializer, Server, ServerRef, ServerSettings}
import service._
import protocols.http._

import net.liftweb.json._
import JsonDSL._
import akka.actor._
import akka.util.ByteString
import scala.concurrent.duration._


object BenchmarkService {

  class Timestamp(server: ServerRef) extends Actor {
        
    val sdf = new SimpleDateFormat("EEE, MMM d yyyy HH:MM:ss z")
    case object Tick
    import context.dispatcher

    override def preStart() {
      self ! Tick
    }

    def receive = {
      case Tick => {
        server.delegatorBroadcast(sdf.format(new Date()))
        context.system.scheduler.scheduleOnce(1.second, self, Tick)
      }
    }
  }
  val response          = ByteString("Hello, World!")
  val plaintextHeader   = HttpResponseHeader("Content-Type", "text/plain")
  val jsonHeader        = HttpResponseHeader("Content-Type", "application/json")
  val serverHeader      = HttpResponseHeader("Server", "Colossus")


  def start(port: Int)(implicit io: IOSystem) {

    val serverConfig = ServerSettings(
      port = port,
      maxConnections = 16384,
      tcpBacklogSize = Some(1024)
    )
    val serviceConfig = ServiceConfig(
      requestMetrics = false
    )

    val server = Server.start("benchmark", serverConfig) { worker => new Initializer(worker) {

      ///the ??? is filled in almost immediately
      var dateHeader = HttpResponseHeader("Date", "???")

      override def receive = {
        case ts: String => dateHeader = HttpResponseHeader("Date", ts)
      }
      
      def onConnect = ctx => new Service[Http](serviceConfig, ctx){ 
        def handle = { case request => 
          if (request.head.url == "/plaintext") {
            val res = HttpResponse(
              version  = HttpVersion.`1.1`,
              code    = HttpCodes.OK,
              data    = response,
              headers = Vector(plaintextHeader, serverHeader, dateHeader)
            )
            Callback.successful(res)
          } else if (request.head.url == "/json") {
            val json = ("message" -> "Hello, World!")
            val res = HttpResponse(
              version  = HttpVersion.`1.1`,
              code    = HttpCodes.OK,
              data    = compact(render(json)),
              headers = Vector(jsonHeader, serverHeader, dateHeader)
            )
            Callback.successful(res)
          } else {
            Callback.successful(request.notFound("invalid path"))
          }
        }
      }
    }}

    val timestamp = io.actorSystem.actorOf(Props(classOf[Timestamp], server))
  }

}

