package org.log4s

import scala.collection.mutable
import scala.util._

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase

/** A custom appender for Logback that captures the events.
  *
  * The test suite can use this to make sure all log events are recorded as expected.
  *
  * @author Sarah Gerweck <sarah@atscale.com>
  */
class TestAppender extends AppenderBase[ILoggingEvent] {
  import TestAppender._

  override def start(): Unit = {
    super.start()
    newQueue()
  }

  override def stop(): Unit = {
    resetQueue()
    super.stop()
  }

  override def append(event: ILoggingEvent): Unit = {
    addEvent(event)
  }
}

object TestAppender {
  private var loggingEvents: Option[mutable.Queue[ILoggingEvent]] = None

  @inline private[this] def events = {
    require(loggingEvents.isDefined, "Illegal operation with no active queue")
    loggingEvents.get
  }

  private def addEvent(event: ILoggingEvent): Unit = synchronized {
    events += event
  }

  def dequeue: Option[ILoggingEvent] = synchronized {
    Try(events.dequeue).toOption
  }

  private def newQueue(): Unit = synchronized {
    loggingEvents match {
      case Some(_) =>
        throw new IllegalStateException("Can't have multiple test appenders")
      case None =>
        loggingEvents = Some(mutable.Queue.empty)
    }
  }

  private def resetQueue(): Unit = synchronized {
    loggingEvents = None
  }
}
