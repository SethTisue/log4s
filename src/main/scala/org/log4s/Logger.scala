package org.log4s

import language.experimental.macros

import java.util.{ Map => JMap }

import scala.collection.JavaConversions._
import scala.reflect.macros.Context

import org.slf4j.{ Logger => JLogger }
import org.slf4j.LoggerFactory.{ getLogger => getJLogger }

object Logger {
  final val singletonsByName = true
  final val trailingDollar = false
  
  @deprecated("0.1", "Use org.log4s.getLogger")
  def getLogger: Logger = macro LoggerMacros.getLoggerImpl
}


final class Logger(val logger: JLogger) extends AnyVal {
  @inline def isTraceEnabled: Boolean = logger.isTraceEnabled
  
  @inline def isDebugEnabled: Boolean = logger.isDebugEnabled
  
  @inline def isInfoEnabled: Boolean = logger.isInfoEnabled
  
  @inline def isWarnEnabled: Boolean = logger.isWarnEnabled
  
  @inline def isErrorEnabled: Boolean = logger.isErrorEnabled
  
  import LoggerMacros._
  
  // TBD: These might benefit from macros?
  @inline def apply(level: LogLevel): LevelLogger = level match {
    case Trace => new TraceLevelLogger(logger)
    case Debug => new DebugLevelLogger(logger)
    case Info  => new InfoLevelLogger(logger)
    case Warn  => new WarnLevelLogger(logger)
    case Error => new ErrorLevelLogger(logger)
  }
  
  def trace(t: Throwable)(msg: String) = macro traceTM
  def trace(msg: String) = macro traceM
  
  def debug(t: Throwable)(msg: String) = macro debugTM
  def debug(msg: String) = macro debugM
  
  def info(t: Throwable)(msg: String) = macro infoTM
  def info(msg: String) = macro infoM
  
  def warn(t: Throwable)(msg: String) = macro warnTM
  def warn(msg: String) = macro warnM
  
  def error(t: Throwable)(msg: String) = macro errorTM
  def error(msg: String) = macro errorM
  
}

sealed trait LevelLogger extends Any {
  def isEnabled: Boolean
  def apply(msg: => String): Unit
}

private final class TraceLevelLogger(val logger: JLogger) extends AnyVal with LevelLogger {
  @inline def isEnabled = logger.isTraceEnabled
  @inline def apply(msg: => String) = if (isEnabled) logger.trace(msg)
}

private final class DebugLevelLogger(val logger: JLogger) extends AnyVal with LevelLogger {
  @inline def isEnabled = logger.isDebugEnabled
  @inline def apply(msg: => String) = if (isEnabled) logger.debug(msg)
}

private final class InfoLevelLogger(val logger: JLogger) extends AnyVal with LevelLogger {
  @inline def isEnabled = logger.isInfoEnabled
  @inline def apply(msg: => String) = if (isEnabled) logger.info(msg)
}

private final class WarnLevelLogger(val logger: JLogger) extends AnyVal with LevelLogger {
  @inline def isEnabled = logger.isWarnEnabled
  @inline def apply(msg: => String) = if (isEnabled) logger.warn(msg)
}

private final class ErrorLevelLogger(val logger: JLogger) extends AnyVal with LevelLogger {
  @inline def isEnabled = logger.isErrorEnabled
  @inline def apply(msg: => String) = if (isEnabled) logger.error(msg)
}

private object LoggerMacros {
  final def getLoggerImpl(c: Context): c.Expr[Logger] = {
    import c.universe._
    
    val cls = c.enclosingClass.symbol

    if (Logger.singletonsByName) {
      if (cls.isModule) {
        val name = c.literal(cls.fullName)
        return reify { new Logger(getJLogger(name.splice)) }
      }
    }
    
    assert(cls.isModule || cls.isClass, "Enclosing class is always either a module or a class")
    
    val tp = if (cls.isModule) cls.asModule.moduleClass else cls
    
    val expr = c.Expr[Class[_]](Literal(Constant(tp.asType.toTypeConstructor)))
    reify { new Logger(getJLogger(expr.splice)) }
  }
  
  
  private[this] type LogCtx = Context { type PrefixType = Logger }
  
  @inline private[this] def reflectiveLog(c: LogCtx)(msg: c.Expr[String], error: Option[c.Expr[Throwable]])(logLevel: String) = {
    import c.universe._
        
    val logger = Select(c.prefix.tree, newTermName("logger"))
    val logValues = error match {
      case None    => List(msg.tree)
      case Some(e) => List(msg.tree, e.tree)
    }
    val logExpr = c.Expr[Unit](Apply(Select(logger, newTermName(logLevel)), logValues))
    @inline def checkExpr = c.Expr[Boolean](Apply(Select(logger, newTermName(s"is${logLevel.capitalize}Enabled")), Nil))
    
    msg match {
      case c.Expr(Literal(Constant(_))) => logExpr
      case _ =>
        reify { if (checkExpr.splice) logExpr.splice }  
    }
  }
  
  final def traceTM(c: LogCtx)(t: c.Expr[Throwable])(msg: c.Expr[String]): c.Expr[Unit] = reflectiveLog(c)(msg, Some(t))("trace")
  final def traceM(c: LogCtx)(msg: c.Expr[String]): c.Expr[Unit] = reflectiveLog(c)(msg, None)("trace")
  
  final def debugTM(c: LogCtx)(t: c.Expr[Throwable])(msg: c.Expr[String]): c.Expr[Unit] = reflectiveLog(c)(msg, Some(t))("debug")
  final def debugM(c: LogCtx)(msg: c.Expr[String]): c.Expr[Unit] = reflectiveLog(c)(msg, None)("debug")
  
  final def infoTM(c: LogCtx)(t: c.Expr[Throwable])(msg: c.Expr[String]): c.Expr[Unit] = reflectiveLog(c)(msg, Some(t))("info")
  final def infoM(c: LogCtx)(msg: c.Expr[String]): c.Expr[Unit] = reflectiveLog(c)(msg, None)("info")
  
  final def warnTM(c: LogCtx)(t: c.Expr[Throwable])(msg: c.Expr[String]): c.Expr[Unit] = reflectiveLog(c)(msg, Some(t))("warn")
  final def warnM(c: LogCtx)(msg: c.Expr[String]): c.Expr[Unit] = reflectiveLog(c)(msg, None)("warn")
  
  final def errorTM(c: LogCtx)(t: c.Expr[Throwable])(msg: c.Expr[String]): c.Expr[Unit] = reflectiveLog(c)(msg, Some(t))("error")
  final def errorM(c: LogCtx)(msg: c.Expr[String]): c.Expr[Unit] = reflectiveLog(c)(msg, None)("error")
  
}
