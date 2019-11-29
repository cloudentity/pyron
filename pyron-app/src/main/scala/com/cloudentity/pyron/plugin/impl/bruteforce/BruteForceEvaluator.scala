package com.cloudentity.pyron.plugin.impl.bruteforce

import java.time.Instant

object BruteForceEvaluator {
  def isBlocked(now: Instant, attempts: List[Attempt]): Boolean =
    attempts.reverse match {
      case last :: _ => {
        val blockForTimestamp = last.timestamp.plusSeconds(last.blockFor)
        last.blocked && blockForTimestamp.isAfter(now)
      }
      case Nil => false
    }

  def shouldBlockNextAttempt(now: Instant, attempts: List[Attempt], maxAttempts: Int, blockSpan: Int): Boolean = {
    val blockSpanStart = now.minusSeconds(blockSpan)
    val attemptsInBlockSpan = attempts.filter(_.timestamp.isAfter(blockSpanStart))
    attemptsInBlockSpan.size + 1 >= maxAttempts
  }
}