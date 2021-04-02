package com.cloudentity.pyron.domain.http

import com.cloudentity.pyron.domain.flow.PathParams
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod

case class OriginalRequest(method: HttpMethod,
                           path: UriPath,
                           scheme: String,
                           host: String,
                           localHost: String,
                           remoteHost: String,
                           pathParams: PathParams,
                           queryParams: QueryParams,
                           headers: Headers,
                           cookies: Map[String, String],
                           bodyOpt: Option[Buffer])
