package com.cloudentity.edge.plugin.authn

import com.cloudentity.edge.plugin.impl.authn.HmacKeyDecryptionError
import com.cloudentity.edge.plugin.impl.authn.HmacKeyDecryptor._
import org.junit.runner.RunWith
import org.scalatest.{MustMatchers, WordSpec}
import org.scalatest.junit.JUnitRunner
import scalaz.{-\/, \/-}

@RunWith(classOf[JUnitRunner])
class HmacKeyDecryptorTest extends WordSpec with MustMatchers {

  val salt = "salt123"
  val encryptedKey = "Xa/CkFXgp+lX7plutJHaQx9vrjIPwmfFFrPNDPS+iPc="
  val validEncryptionKey = "abcdef12345667"

  "Hmac key decryptor" should {
    "decrypt encrypted api key" in {
      decrypt(encryptedKey, validEncryptionKey, salt) mustBe \/-("some_apikey")
    }
    "fail with proper exception if password is not valid" in {
      decrypt(encryptedKey, "someEncryptionKey", salt) mustBe
        -\/(HmacKeyDecryptionError("Given final block not properly padded. Such issues can arise if a bad key is used during decryption."))
    }
    "fail with proper exception if salt is not valid" in {
      decrypt(encryptedKey, validEncryptionKey, "otherSalt") mustBe
        -\/(HmacKeyDecryptionError("Last unit does not have enough valid bits"))
    }
    "fail with proper exception if key was encrypted with other salt & encryption key" in {
      decrypt("Fl8X8U9rEBRf45sMdFYsrXAZuud+Ynb/CJmuz8ElW3M=", validEncryptionKey, salt) mustBe
        -\/(HmacKeyDecryptionError("Given final block not properly padded. Such issues can arise if a bad key is used during decryption."))
    }
  }
}
