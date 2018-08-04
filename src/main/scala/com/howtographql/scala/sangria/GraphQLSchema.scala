package com.howtographql.scala.sangria

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import com.howtographql.scala.sangria.models._
import sangria.ast.StringValue
import sangria.execution.deferred._
import sangria.execution.{HandledException, ExceptionHandler => EHandler}
import sangria.macros.derive._
import sangria.schema.{Field, ListType, ObjectType, _}

object GraphQLSchema {

  implicit val GraphQLDateTime = ScalarType[LocalDateTime](
    "DateTime",
    coerceOutput = (dt, _) => dt.toString,
    coerceInput = {
      case StringValue(dt, _, _) => Right(LocalDateTime.parse(dt, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
      case _ => Left(DateTimeCoerceViolation)
    },
    coerceUserInput = {
      case s: String => Right(LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
      case _ => Left(DateTimeCoerceViolation)
    }
  )

  val identifiableType = InterfaceType(
    "Identifiable",
    fields[Unit, Identifiable](
      Field("id", IntType, resolve = _.value.id)
    )
  )

  lazy val LinkType : ObjectType[Unit, Link] = deriveObjectType[Unit, Link](
    Interfaces(identifiableType),
    ReplaceField("postedBy", Field("postedBy", UserType, resolve = c => usersFetcher.defer(c.value.postedBy))),
    ReplaceField("createdAt", Field("createdAt", GraphQLDateTime, resolve = _.value.createdAt)),
    AddFields(
      Field("votes", ListType(VoteType), resolve = c => votesFetcher.deferRelSeq(votesByLinkRel, c.value.id))
    )
  )
//  val LinkType = ObjectType[Unit, Link](
//    "Link",
//    fields[Unit, Link](
//      Field("id", IntType, resolve = _.value.id),
//      Field("url", StringType, resolve = _.value.url),
//      Field("description", StringType, resolve = _.value.description)
//    )
//  )

  lazy val UserType: ObjectType[Unit, User] = deriveObjectType[Unit, User](
    Interfaces(identifiableType),
    ReplaceField("createdAt", Field("createdAt", GraphQLDateTime, resolve = _.value.createdAt)),
    AddFields(
      Field("links", ListType(LinkType), resolve = c => linksFetcher.deferRelSeq(linksByUserRel, c.value.id)),
      Field("votes", ListType(VoteType), resolve = c => votesFetcher.deferRelSeq(votesByUserRel, c.value.id))
    )
  )

  lazy val VoteType: ObjectType[Unit, Vote] = deriveObjectType[Unit, Vote](
    Interfaces(identifiableType),
    ExcludeFields("userId", "linkId"),
    ReplaceField("createdAt", Field("createdAt", GraphQLDateTime, resolve = _.value.createdAt)),
    AddFields(
      Field("user", UserType, resolve = c => usersFetcher.defer(c.value.userId)),
      Field("link", LinkType, resolve = c => linksFetcher.defer(c.value.linkId))
    )
  )

//  implicit val authProviderEmailInputType : InputObjectType[AuthProviderEmail] = deriveInputObjectType[AuthProviderEmail(
//    InputObjectTypeName("AUTH_PROVIDER_EMAIL")
//  )

  val linksByUserRel = Relation[Link, Int]("linksByUser", l => Seq(l.postedBy))
  val votesByUserRel = Relation[Vote, Int]("votesByUser", l => Seq(l.userId))
  val votesByLinkRel = Relation[Vote, Int]("votesOnLink", l => Seq(l.linkId))

  val linksFetcher = Fetcher.rel(
    (ctx: MyContext, ids: Seq[Int]) => ctx.dao.getLinks(ids),
    (ctx: MyContext, ids: RelationIds[Link]) => ctx.dao.getLinksByUserIds(ids(linksByUserRel))
  )
  val usersFetcher = Fetcher((ctx: MyContext, ids: Seq[Int]) => ctx.dao.getUsers(ids))

  val votesFetcher = Fetcher.rel(
    (ctx: MyContext, ids: Seq[Int]) => ctx.dao.getVotes(ids),
    (ctx: MyContext, ids: RelationIds[Vote]) => ctx.dao.getVotesByRelationIds(ids, votesByUserRel, votesByLinkRel)
  )

  val Resolver = DeferredResolver.fetchers(linksFetcher, usersFetcher, votesFetcher)

  val Id = Argument("id", IntType)
  val Ids = Argument("ids", ListInputType(IntType))

  val QueryType = ObjectType(
    "Query",
    fields[MyContext, Unit](
      Field("allLinks", ListType(LinkType), resolve = c => c.ctx.dao.allLinks),
      Field("link", OptionType(LinkType), arguments = Id :: Nil, resolve = c => linksFetcher.deferOpt(c.arg(Id))),
      Field("links", ListType(LinkType), arguments = Ids :: Nil, resolve = c => linksFetcher.deferSeq(c.arg(Ids))),
      Field("allUser", ListType(UserType), resolve = c => c.ctx.dao.allUsers),
      Field("user", OptionType(UserType), arguments = Id :: Nil, resolve = c => usersFetcher.deferOpt(c.arg(Id))),
      Field("users", ListType(UserType), arguments = Ids :: Nil, resolve = c => usersFetcher.deferSeq(c.arg(Ids))),
      Field("allVotes", ListType(VoteType), resolve = c => c.ctx.dao.allVotes),
      Field("vote", OptionType(VoteType), arguments = Id :: Nil, resolve = c => votesFetcher.deferOpt(c.arg(Id))),
      Field("votes", ListType(VoteType), arguments = Ids :: Nil, resolve = c => votesFetcher.deferSeq(c.arg(Ids)))
    )
  )

  val NameArg = Argument("name", StringType)
  val emailArg = Argument("email", StringType)
  val passwordArg = Argument("password", StringType)

  val urlArg = Argument("url", StringType)
  val descriptionArg = Argument("description", StringType)
  val postedByArg = Argument("postedBy", IntType)

  val userIdArg = Argument("userId", IntType)
  val linkIdArg = Argument("linkId", IntType)

  val Mutation = ObjectType(
    "Mutation",
    fields[MyContext, Unit](
      Field("createUser", UserType, arguments = NameArg :: emailArg :: passwordArg :: Nil,
        resolve = c => c.ctx.dao.createUser(c.arg(NameArg), c.arg(emailArg), c.arg(passwordArg))
      ),
       Field("createLink", LinkType, arguments = urlArg :: descriptionArg :: postedByArg :: Nil,
         tags = Authorized :: Nil,
         resolve = c => c.ctx.dao.createLink(c.arg(urlArg), c.arg(descriptionArg), c.arg(postedByArg))
      ),
      Field("createVote", VoteType, arguments = userIdArg :: linkIdArg :: Nil,
        tags = Authorized :: Nil,
        resolve = c => c.ctx.dao.createVote(c.arg(userIdArg), c.arg(linkIdArg))
      ),
      Field("login", UserType, arguments = emailArg :: passwordArg :: Nil,
        resolve = c => UpdateCtx(c.ctx.login(c.arg(emailArg), c.arg(passwordArg)))
        {user => c.ctx.copy(currentUser = Some(user))}
      )
    )
  )

  val SchemaDefinition = Schema(QueryType, Some(Mutation))

  val ErrorHandler = EHandler {
    case (_, AuthenticationException(message)) => HandledException(message)
    case (_, AuthorizationException(message)) => HandledException(message)
  }
}
