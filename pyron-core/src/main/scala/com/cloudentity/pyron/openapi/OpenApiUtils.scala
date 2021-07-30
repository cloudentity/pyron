package com.cloudentity.pyron.openapi

import com.cloudentity.pyron.domain.flow.{PathParamName, PathParams, PathPattern, PathPrefix}

import java.util
import com.cloudentity.pyron.domain.openapi.OpenApi.OpenApiOperations
import com.cloudentity.pyron.domain.openapi.OpenApiRule
import io.swagger.models._
import io.swagger.models.parameters._
import io.swagger.models.properties.Property

import scala.collection.JavaConverters._
import scala.util.matching.Regex

object OpenApiConverterUtils extends OpenApiConverterUtils
trait OpenApiConverterUtils {

  def buildPath(operations: OpenApiOperations): Path = operations.foldLeft(new Path()) {
    case (path, (method, operation)) => path.set(method.toString.toLowerCase, operation)
  }

  def buildAbsolutePathsMap(swagger: Swagger): Map[String, OpenApiOperations] = {
    swagger.getPaths.asScala.map {
      case (key, value) => buildAbsolutePath(swagger, key) -> value.getOperationMap.asScala.toMap
    }.toMap
  }

  def buildAbsolutePath(swagger: Swagger, path: String): String =
    Option(swagger.getBasePath) match {
      case Some(basePath) =>
        if (basePath.endsWith("/")) basePath.dropRight(1) + path
        else basePath + path
      case None => path
    }

  def findOperation(swagger: Swagger, path: String, method: HttpMethod): Option[Operation] = {
    findPath(swagger, path).flatMap(p => findOperationInPath(p, method))
  }

  def findOperationWithAbsoluteUrl(swagger: Swagger, path: String, method: HttpMethod): Option[Operation] = {
    swagger.getPaths.asScala.find { case (k, _) =>
      path == buildAbsolutePath(swagger, k)
    }.flatMap(e => findOperationInPath(e._2, method))
  }

  def findPath(swagger: Swagger, targetServicePath: String): Option[Path] =
    swagger.getPaths.asScala.find(x => pathMatches(targetServicePath, x._1)).map(_._2)

  def findOperationInPath(path: Path, method: HttpMethod): Option[Operation] =
    Option(path.getOperationMap.get(method))

  def buildPaths(pathsMap: Map[String, OpenApiOperations]): Map[String, Path] = {
    pathsMap.mapValues(buildPath)
  }

  def pathMatches(testPath: String, regexPath: String): Boolean = {
    val normalizedTargetServicePath = testPath.replaceAll("\\{|\\}", "")
    makeMatch(normalizedTargetServicePath, PathMatching.build(PathPrefix(""), PathPattern(regexPath))).isDefined
  }

  def makeMatch(path: String, matcher: PathMatching): Option[PathParams] =
    matcher.regex.findFirstMatchIn(path).flatMap[PathParams] { mtch =>
      if (mtch.groupCount == matcher.paramNames.size)
        Some(
          PathParams(
            matcher.paramNames.map { name =>
              name.value -> mtch.group(name.value)
            }.toMap
          )
        )
      else None
    }

  def toSwaggerMethod(method: io.vertx.core.http.HttpMethod): io.swagger.models.HttpMethod = {
    io.swagger.models.HttpMethod.valueOf(method.toString.toUpperCase)
  }

  def deepCopyOperation(operation: Operation): Operation =
    SwaggerCopy.copyOperation(operation)

}

case class PathMatching(regex: Regex, paramNames: List[PathParamName], prefix: PathPrefix, originalPath: String)

object PathMatching {
  val namePlaceholderPattern = """\{\w+[^/]\}"""
  val namePlaceholderRegex = namePlaceholderPattern.r

  def build(pathPrefix: PathPrefix, pathPattern: PathPattern): PathMatching =
    PathMatching(
      regex = createPatternRegex(createRawPattern(pathPrefix, pathPattern)),
      paramNames = extractPathParamNames(pathPattern),
      prefix = pathPrefix,
      originalPath = pathPattern.value
    )

  def createRawPattern(pathPrefix: PathPrefix, pathPattern: PathPattern): String =
    pathPrefix.value + pathPattern.value

  def createPatternRegex(rawPattern: String): Regex = {
    val regex = namePlaceholderRegex.findAllIn(rawPattern).toList.foldLeft(rawPattern) { case (acc, mtch) =>
      val name = mtch.drop(1).dropRight(1)
      acc.replaceFirst(namePlaceholderPattern, s"(?<$name>[^/]+)")
    }
    s"^$regex$$".r
  }

  def extractPathParamNames(pattern: PathPattern): List[PathParamName] =
    namePlaceholderRegex.findAllIn(pattern.value).toList.map(_.drop(1).dropRight(1)).map(PathParamName)
}

object OpenApiPluginUtils extends OpenApiPluginUtils
trait OpenApiPluginUtils {
  def findOperation(swagger: Swagger, rule: OpenApiRule): Option[Operation] = {
    val method = OpenApiConverterUtils.toSwaggerMethod(rule.rewriteMethod.map(_.value).getOrElse(rule.method))
    OpenApiConverterUtils.findOperationWithAbsoluteUrl(swagger, rule.apiGwPath, method)
  }
}

object SwaggerCopy extends SwaggerCopy
trait SwaggerCopy {

  implicit class CopyList[A](value: util.List[A]) {
    def copy(cp: A => A): util.List[A] =
      Option(value).map { as =>
        val result = new util.ArrayList[A]()
        as.forEach(value => result.add(cp(value)))
        result
      }.orNull
  }

  implicit class CopyMap[A](value: util.Map[String, A]) {
    def copy(cp: A => A): util.Map[String, A] =
      Option(value).map { as =>
        val result = new util.HashMap[String, A]()
        as.forEach((key, value) => result.put(key, cp(value)))
        result
      }.orNull
  }

  def copyOperation(o: Operation): Operation =
    if (o == null) o
    else {
      val no = new Operation()

      no.setConsumes(o.getConsumes.copy(identity))
      no.setDescription(o.getDescription)
      no.setExternalDocs(copyExternalDocs(o.getExternalDocs))
      no.setOperationId(o.getOperationId)
      no.setParameters(o.getParameters.copy(copyParameter))
      no.setProduces(o.getProduces.copy(identity))
      no.setResponses(o.getResponses.copy(copyResponse))
      no.setSchemes(o.getSchemes.copy(copyScheme))
      no.setSecurity(copyListOfMaps(o.getSecurity))
      no.setSummary(o.getSummary)
      no.setTags(o.getTags.copy(identity))
      no.setVendorExtensions(o.getVendorExtensions.copy(identity))
      no.setDeprecated(o.isDeprecated)

      no
    }

  type ListOfMaps = util.List[util.Map[String, util.List[String]]]
  def copyListOfMaps(o: ListOfMaps): ListOfMaps=
    if (o == null) o
    else {
      o.asScala.map(_.asScala.mapValues(_.asScala.asJava).asJava).asJava
    }

  def copyParameter(o: Parameter): Parameter =
    if (o == null) o
    else {
      def setBaseAttributes(a: Parameter): Unit = {
        a.setAccess(o.getAccess)
        a.setDescription(o.getDescription)
        a.setIn(o.getIn)
        a.setName(o.getName)
        a.setPattern(o.getPattern)
        a.setRequired(o.getRequired)
        a.setReadOnly(o.isReadOnly)
      }

      def setAbstractSerializableAttributes(from: AbstractSerializableParameter[_], to: AbstractSerializableParameter[_]): Unit = {
        to.setAllowEmptyValue(from.getAllowEmptyValue)
        to.setCollectionFormat(from.getCollectionFormat)
        to.setDefault(from.getDefault)
        to.setDefaultValue(Option(from.getDefaultValue).map(_.toString).orNull)
        to.setEnum(from.getEnum.copy(identity))
        to.setEnumValue(from.getEnumValue.copy(identity))
        to.setExample(Option(from.getExample).map(_.toString).orNull)
        to.setExclusiveMaximum(from.isExclusiveMaximum)
        to.setExclusiveMinimum(from.isExclusiveMinimum)
        to.setFormat(from.getFormat)
        to.setItems(copyProperty(from.getItems))
        to.setMaximum(from.getMaximum)
        to.setMaxItems(from.getMaxItems)
        to.setMaxLength(from.getMaxLength)
        to.setMinimum(from.getMinimum)
        to.setMinItems(from.getMinItems)
        to.setMinLength(from.getMinLength)
        to.setMultipleOf(from.getMultipleOf)
        to.setType(from.getType)
        to.setUniqueItems(from.isUniqueItems)
        to.setVendorExtensions(from.getVendorExtensions.copy(identity))
      }

      o match {
        case p: BodyParameter =>
          val no = new BodyParameter()
          setBaseAttributes(no)
          no.setExamples(p.getExamples.copy(identity))
          no.setSchema(copyModel(p.getSchema))
          no

        case p: CookieParameter =>
          val no = new CookieParameter()
          setBaseAttributes(no)
          setAbstractSerializableAttributes(p, no)
          no

        case p: FormParameter =>
          val no = new FormParameter()
          setBaseAttributes(no)
          setAbstractSerializableAttributes(p, no)
          no

        case p: QueryParameter =>
          val no = new QueryParameter()
          setBaseAttributes(no)
          setAbstractSerializableAttributes(p, no)
          no

        case p: PathParameter =>
          val no = new PathParameter()
          setBaseAttributes(no)
          setAbstractSerializableAttributes(p, no)
          no

        case p: HeaderParameter =>
          val no = new HeaderParameter()
          setBaseAttributes(no)
          setAbstractSerializableAttributes(p, no)
          no

        case p: RefParameter =>
          val no = new RefParameter(p.get$ref())
          setBaseAttributes(no)
          no
      }
    }

  def copyExternalDocs(o: ExternalDocs): ExternalDocs =
    if (o == null) o
    else {
      val no = new ExternalDocs()
      no.setDescription(o.getDescription)
      no.setUrl(o.getUrl)
      no.setVendorExtensions(o.getVendorExtensions.copy(identity))
      no
    }

  def copyResponse(o: Response): Response =
    if (o == null) o
    else {
      val no = new Response
      no.setDescription(o.getDescription)
      no.setExamples(o.getExamples.copy(identity))
      no.setHeaders(o.getHeaders.copy(copyProperty))
      no.setResponseSchema(copyModel(o.getResponseSchema))
      no.setVendorExtensions(o.getVendorExtensions.copy(identity))
      no
    }

  def copyScheme(o: Scheme): Scheme =
    if (o == null) o
    else {
      Scheme.forValue(o.toValue)
    }

  def copyModel(o: Model): Model =
    if (o == null) o
    else {
      def setBaseAttributes(from: AbstractModel, to: AbstractModel): Unit = {
        to.setDescription(from.getDescription)
        to.setExample(from.getExample)
        to.setProperties(from.getProperties.copy(copyProperty))
        to.setReference(from.getReference)
        to.setTitle(from.getTitle)
        to.setExample(Option(from.getExample).map(_.toString).orNull)
        to.setExclusiveMaximum(from.getExclusiveMaximum)
        to.setExclusiveMinimum(from.getExclusiveMinimum)
        to.setMaximum(from.getMaximum)
        to.setMaxLength(from.getMaxLength)
        to.setMinimum(from.getMinimum)
        to.setMinLength(from.getMinLength)
        to.setMultipleOf(from.getMultipleOf)
        to.setExternalDocs(copyExternalDocs(from.getExternalDocs))
        to.setPattern(from.getPattern)
        to.setVendorExtensions(from.getVendorExtensions.copy(identity))
        to.setXml(copyXml(from.getXml))
      }

      def copyRefModel(rm: RefModel): RefModel = if (rm == null) rm else {
        val nrm = new RefModel
        nrm.set$ref(rm.get$ref())
        nrm.setDescription(rm.getDescription)
        nrm.setExample(rm.getExample)
        nrm.setExternalDocs(copyExternalDocs(rm.getExternalDocs))
        nrm.setProperties(rm.getProperties.copy(copyProperty))
        nrm.setReference(rm.getReference)
        nrm.setTitle(rm.getTitle)
        nrm
      }

      o match {
        case m: ArrayModel =>
          val nm = new ArrayModel
          setBaseAttributes(m, nm)
          nm.setItems(copyProperty(m.getItems))
          nm.setMaxItems(m.getMaxItems)
          nm.setMinItems(m.getMinItems)
          nm.setType(m.getType)
          nm.setUniqueItems(m.getUniqueItems)
          nm

        case m: ComposedModel =>
          val nm = new ComposedModel
          setBaseAttributes(m, nm)
          nm.setAllOf(m.getAllOf.copy(copyModel))
          nm.setChild(copyModel(m.getChild))
          nm.setParent(copyModel(m.getParent))
          nm.setInterfaces(m.getInterfaces.copy(copyRefModel))
          nm

        case m: ModelImpl =>
          val nm = new ModelImpl
          setBaseAttributes(m, nm)
          nm.setAdditionalProperties(copyProperty(m.getAdditionalProperties))
          nm.setAllowEmptyValue(m.getAllowEmptyValue)
          nm.setDefaultValue(Option(m.getDefaultValue).map(_.toString).orNull)
          nm.setDiscriminator(m.getDiscriminator)
          nm.setEnum(m.getEnum.copy(identity))
          nm.setFormat(m.getFormat)
          nm.setType(m.getType)
          nm.setName(m.getName)
          nm.setRequired(m.getRequired.copy(identity))
          nm.setSimple(m.isSimple)
          nm.setUniqueItems(m.getUniqueItems)
          nm

        case m: RefModel =>
          copyRefModel(m)
      }
    }

  def copyProperty(o: Property): Property =
    if (o == null) o
    else {
      o.rename(o.getName)
    }

  def copyXml(o: Xml): Xml =
    if (o == null) o
    else {
      o.clone().asInstanceOf[Xml]
    }
}
