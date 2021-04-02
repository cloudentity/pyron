package com.cloudentity.pyron.domain.http

case class CallOpts(responseTimeout: Option[Int],
                    retries: Option[Int],
                    failureHttpCodes: Option[List[Int]],
                    retryFailedResponse: Option[Boolean],
                    retryOnException: Option[Boolean])
