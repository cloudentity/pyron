package com.cloudentity.pyron.plugin.impl.bruteforce

import java.time.Instant

object BruteForceEvaluator {
  def isBlocked(now: Instant, attempts: List[Attempt]): Boolean =
    attempts.headOption match {
      case Some(attempt) => {
        val blockForTimestamp = attempt.timestamp.plusSeconds(attempt.blockFor)
        attempt.blocked && blockForTimestamp.isAfter(now)
      }
      case None => false
    }

  def shouldBlockNextAttempt(now: Instant, attempts: List[Attempt], maxAttempts: Int, blockSpan: Int): Boolean = {
    val blockSpanStart = now.minusSeconds(blockSpan)
    val attemptsInBlockSpan = attempts.filter(_.timestamp.isAfter(blockSpanStart))
    attemptsInBlockSpan.size + 1 >= maxAttempts
  }
}