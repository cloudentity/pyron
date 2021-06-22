package com.cloudentity.pyron.domain.http

import com.cloudentity.pyron.domain.flow.PathParams
import com.cloudentity.pyron.domain.http.Cookie.Cookies
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
                           cookies: Cookies,
                           bodyOpt: Option[Buffer])
