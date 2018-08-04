package com.howtographql.scala.sangria

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import sangria.ast.Document
import sangria.execution._
import sangria.marshalling.sprayJson._
import sangria.parser.QueryParser
import spray.json.{JsObject, JsString, JsValue}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}


object GraphQLServer {

  // 1
  private val dao = DBSchema.createDatabase

  // 2
  def endpoint(requestJSON: JsValue)(implicit ec: ExecutionContext): Route = {

    // 3
    val JsObject(fields) = requestJSON

    // 4
    val JsString(query) = fields("query")

    // 5
    QueryParser.parse(query) match {
      case Success(queryAst) =>
        // 6
        val operation = fields.get("operationName") collect {
          case JsString(op) => op
        }

        // 7
        val variables = fields.get("variables") match {
          case Some(obj: JsObject) => obj
          case _ => JsObject.empty
        }
        // 8
        complete(executeGraphQLQuery(queryAst, operation, variables))
      case Failure(error) =>
        complete(BadRequest, JsObject("error" -> JsString(error.getMessage)))
    }

  }

  private def executeGraphQLQuery(query: Document, operation: Option[String], vars: JsObject)(implicit ec: ExecutionContext) = {
    // 9
    Executor.execute(
      GraphQLSchema.SchemaDefinition, // 10
      query, // 11
      MyContext(dao), // 12
      variables = vars, // 13
      operationName = operation, // 14
      deferredResolver = GraphQLSchema.Resolver,
      exceptionHandler = GraphQLSchema.ErrorHandler,
      middleware = AuthMiddleware :: Nil
    ).map(OK -> _)
      .recover {
      case error: QueryAnalysisError => BadRequest -> error.resolveError
      case error: ErrorWithResolver => InternalServerError -> error.resolveError
    }
  }

}

