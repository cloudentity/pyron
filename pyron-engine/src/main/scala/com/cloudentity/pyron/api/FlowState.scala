package com.cloudentity.pyron.api

import com.cloudentity.pyron.domain.flow.{AccessLogItems, AuthnCtx, FlowFailure, Properties}
import com.cloudentity.pyron.rule.Rule

case class FlowState(authnCtx: Option[AuthnCtx],
                     rules: List[Rule],
                     aborted: Option[Boolean],
                     failure: Option[FlowFailure],
                     extraAccessLogs: AccessLogItems,
                     properties: Properties)

object FlowState {
  def empty: FlowState = FlowState(None, Nil, None, None, AccessLogItems(), Properties())
}
