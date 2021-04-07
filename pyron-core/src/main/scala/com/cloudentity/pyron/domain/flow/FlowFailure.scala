package com.cloudentity.pyron.domain.flow

sealed trait FlowFailure
case object RequestPluginFailure extends FlowFailure
case object ResponsePluginFailure extends FlowFailure
case object CallFailure extends FlowFailure
