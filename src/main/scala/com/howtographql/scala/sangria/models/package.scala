package com.howtographql.scala.sangria

import java.time.LocalDateTime

import sangria.execution.FieldTag
import sangria.execution.deferred.HasId
import sangria.validation.Violation

package object models {

  trait Identifiable{
    val id: Int
  }

  object Identifiable {
    implicit def hasId[T <: Identifiable]: HasId[T, Int] = HasId(_.id)
  }

  case object DateTimeCoerceViolation extends Violation {
    override def errorMessage: String = "Error during parsing DateTime"
  }

  case class Link(id: Int, url: String, description: String, postedBy: Int, createdAt: LocalDateTime) extends Identifiable
  case class User(id:Int, name: String, email: String, password:String, createdAt:LocalDateTime) extends Identifiable
  case class Vote(id:Int, createdAt:LocalDateTime, userId: Int, linkId: Int) extends Identifiable

  case class AuthenticationException(message: String) extends Exception(message)
  case class AuthorizationException(message: String) extends Exception(message)

  case object Authorized extends FieldTag
}
