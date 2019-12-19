package com.cloudentity.pyron.plugin.impl.bruteforce

import java.time.Instant

import org.junit.{Assert, Test}

class BruteForceEvaluatorTest {
  @Test
  def shouldBeBlockedIfLastAttemptWasMarkedBlockingAndWithinBlockFor(): Unit = {
    // given
    val attempts = List(Attempt(true, Instant.ofEpochMilli(1000), 10))

    // when
    val blocked = BruteForceEvaluator.isBlocked(Instant.ofEpochMilli(1000).plusSeconds(9), attempts)

    // then
    Assert.assertTrue(blocked);
  }

  @Test
  def shouldNotBeBlockedIfLastAttemptWasMarkedBlockingAndOutOfBlockFor(): Unit = {
    // given
    val attempts = List(Attempt(true, Instant.ofEpochMilli(1000), 10))

    // when
    val blocked = BruteForceEvaluator.isBlocked(Instant.ofEpochMilli(1000).plusSeconds(11), attempts)

    // then
    Assert.assertFalse(blocked);
  }

  @Test
  def shouldNotBeBlockedIfLastAttemptWasNotMarkedBlockingAndWithinBlockFor(): Unit = {
    // given
    val attempts = List(Attempt(false, Instant.ofEpochMilli(1000), 10))

    // when
    val blocked = BruteForceEvaluator.isBlocked(Instant.ofEpochMilli(1000).plusSeconds(9), attempts)

    // then
    Assert.assertFalse(blocked);
  }

  @Test
  def shouldNextBeBlockedIfAttemptsCountEqualsMaxAttemptsAndWithinBlockSpan(): Unit = {
    // given
    val attempts =
      List(
        Attempt(false, Instant.ofEpochMilli(1000), 10),
        Attempt(false, Instant.ofEpochMilli(1001), 10)
      )

    // when
    val blockNext = BruteForceEvaluator.shouldBlockNextAttempt(Instant.ofEpochMilli(1000).plusSeconds(3), attempts, 3, 5)

    // then
    Assert.assertTrue(blockNext);
  }

  @Test
  def shouldNextBeNotBlockedIfAttemptsCountLowerThanMaxAttemptsAndAllWithinBlockSpan(): Unit = {
    // given
    val attempts =
      List(
        Attempt(false, Instant.ofEpochMilli(1000), 10)
      )

    // when
    val blockNext = BruteForceEvaluator.shouldBlockNextAttempt(Instant.ofEpochMilli(1000).plusSeconds(3), attempts, 3, 5)

    // then
    Assert.assertFalse(blockNext);
  }

  @Test
  def shouldNextBeNotBlockedIfAttemptsCountLowerThanMaxAttemptsAndSomeNotWithinBlockSpan(): Unit = {
    // given
    val attempts =
      List(
        Attempt(false, Instant.ofEpochMilli(1000), 10),
        Attempt(false, Instant.ofEpochMilli(1001), 10),
        Attempt(false, Instant.ofEpochMilli(5000), 10)
      )

    // when
    val blockNext = BruteForceEvaluator.shouldBlockNextAttempt(Instant.ofEpochMilli(5000).plusSeconds(3), attempts, 3, 5)

    // then
    Assert.assertFalse(blockNext);
  }

}
