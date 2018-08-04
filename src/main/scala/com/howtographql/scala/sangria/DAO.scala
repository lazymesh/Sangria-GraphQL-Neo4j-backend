package com.howtographql.scala.sangria
import java.time.LocalDateTime

import com.howtographql.scala.sangria.models.{Link, User, Vote}
import org.neo4j.driver.v1.{Driver, Record}
import sangria.execution.deferred.{Relation, RelationIds}

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters

class DAO(connection: Driver) {

  def allLinks = {
    val queryString = "MATCH (n: Link) RETURN n.id as id, n.url as url, n.description as description, n.postedBy as postedBy, n.createdAt as createdAt"
    getData(queryString, readLink)
  }

  def getLinks(ids: Seq[Int]) = {
    val stringIds = ids.mkString(",")
    val queryString = s"MATCH (n: Link) WHERE n.id IN [${stringIds}] RETURN n.id as id, n.url as url, n.description as description, n.postedBy as postedBy, n.createdAt as createdAt"
    getData(queryString, readLink)
  }

  def allUsers = {
    val session = connection.session()
    val queryString = "MATCH (n: User) RETURN n.id as id, n.name as name, n.email as email, n.password as password, n.createdAt as createdAt"
    getData(queryString, readUser)
  }

  def getUsers(ids: Seq[Int]) = {
    val stringIds = ids.mkString(",")
    val queryString = s"MATCH (n: User) WHERE n.id IN [${stringIds}] RETURN n.id as id, n.name as name, n.email as email, n.password as password, n.createdAt as createdAt"
    getData(queryString, readUser)
  }

  def allVotes = {
    val queryString = "MATCH (n: Vote) RETURN n.id as id, n.createdAt as createdAt, n.userId as userId, n.linkId as linkId"
    getData(queryString, readVote)
  }

  def getVotes(ids: Seq[Int]) = {
    val stringIds = ids.mkString(",")
    val queryString = s"MATCH (n: Vote) WHERE n.id IN [${stringIds}] RETURN n.id as id, n.createdAt as createdAt, n.userId as userId, n.linkId as linkId"
    getData(queryString, readVote)
  }

  def getLinksByUserIds(ids: Seq[Int]) = {
    val stringIds = ids.mkString(",")
    val queryString = s"MATCH (n: Link) WHERE n.postedBy IN [${stringIds}] RETURN n.id as id, n.url as url, n.description as description, n.postedBy as postedBy, n.createdAt as createdAt"
    getData(queryString, readLink)
  }

  def getVotesByRelationIds(rel: RelationIds[Vote], votesByUserRel: Relation[Vote, Vote, Int], votesByLinkRel: Relation[Vote, Vote, Int]) = {

    val userIds = rel(votesByUserRel)
    val linkIds = rel(votesByLinkRel)
    val queryString = s"MATCH (n: Vote) WHERE n.userId IN [${userIds.mkString(",")}] OR n.linkId IN [${linkIds.mkString(",")}] RETURN n.id as id, n.createdAt as createdAt, n.userId as userId, n.linkId as linkId"
    getData(queryString, readVote)
  }

  def createUser(name: String, email: String, password: String) = {

    val queryString = s"""CREATE (n: User{id: 0, name: "${name}", email:"$email", password:"$password", createdAt:$getTodayDateTimeNeo4j}) RETURN ID(n) as id, n.name as name, n.email as email, n.password as password, n.createdAt as createdAt"""
    writeData(queryString, readUser)
  }

  def createLink(url: String, description: String, postedBy: Int) = {

    val queryString = s"""CREATE (n: Link{id: 0, url: "$url", description:"$description", postedBy:$postedBy, createdAt:$getTodayDateTimeNeo4j}) RETURN ID(n) as id, n.url as url, n.description as description, n.postedBy as postedBy, n.createdAt as createdAt"""
    writeData(queryString, readLink)
  }

  def createVote(userId: Int, linkId: Int) = {

    val queryString = s"""CREATE (n: Vote{id: 0, createdAt:$getTodayDateTimeNeo4j, userId:$userId, linkId:$linkId}) RETURN n.id as id, n.createdAt as createdAt, n.userId as userId, n.linkId as linkId"""
    writeData(queryString, readVote)
  }

  def getTodayDateTimeNeo4j(): String ={
    s"""localdatetime(
      |{date:date({ year:${LocalDateTime.now.getYear}, month:${LocalDateTime.now.getMonthValue}, day:${LocalDateTime.now.getDayOfMonth}}),
      | time: localtime({ hour:${LocalDateTime.now.getHour}, minute:${LocalDateTime.now.getMinute}, second:${LocalDateTime.now.getSecond}})})
      | """.stripMargin
  }

  def getData[T](query: String, reader : Record => T) = {
    val session = connection.session()

    val queryCompletion = session
      .runAsync(query)
      .thenCompose[java.util.List[T]](c => c.listAsync[T](record => reader(record)))
      .thenApply[Seq[T]] { _.asScala }
      .whenComplete((_, _) => session.closeAsync())

    FutureConverters.toScala(queryCompletion)
  }

  def writeData[T](query: String, reader : Record => T) = {
    val session = connection.session()

    val queryCompletion = session
      .runAsync(query)
      .thenCompose[java.util.List[T]](c => c.listAsync[T](r => reader(r)))
      .thenApply[T]{ _.asScala.head }
      .whenComplete((_, _) => session.closeAsync())

    FutureConverters.toScala(queryCompletion)
  }

  def readLink(record: Record): Link= {
    Link(id = record.get("id").asInt(), url = record.get("url").asString(), description = record.get("description").asString(), postedBy = record.get("postedBy").asInt, createdAt = record.get("createdAt").asLocalDateTime())
  }

  def readUser(record: Record): User = {
    User(id = record.get("id").asInt(), name = record.get("name").asString(), email = record.get("email").asString(), password = record.get("password").asString(), createdAt = record.get("createdAt").asLocalDateTime())
  }

  def readVote(record: Record): Vote = {
    Vote(id = record.get("id").asInt(), createdAt = record.get("createdAt").asLocalDateTime(), userId = record.get("userId").asInt(), linkId = record.get("linkId").asInt())
  }

  def authenticate(email: String, password: String) ={
    val session = connection.session()

    val query = s"""match(n: User) where n.email = "$email" AND n.password = "$password" RETURN ID(n) as id, n.name as name, n.email as email, n.password as password, n.createdAt as createdAt"""

    FutureConverters.toScala(session
      .runAsync(query)
      .thenCompose[Record](c => c.nextAsync())
      .thenApply[Option[User]]{ r => Option(r).map(readUser) }
      .whenComplete((_, _) => session.closeAsync()))
  }

}
