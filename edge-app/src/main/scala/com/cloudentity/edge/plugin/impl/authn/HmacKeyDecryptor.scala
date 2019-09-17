package com.cloudentity.edge.plugin.impl.authn

import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets
import java.util.Base64

import scalaz.\/

import scala.util.Try

case class HmacKeyDecryptionError(message: String)

object HmacKeyDecryptor {
  private val CIPHER_SUITE = "AES/CBC/PKCS5Padding"
  private val ITERATIONS = 53
  private val KEY_LENGTH = 256
  private val ENCRYPTION_TYPE = "AES"

  def decrypt(encrypted: String, password: String, salt: String): \/[HmacKeyDecryptionError, String] =
    \/.fromTryCatchThrowable[String, Throwable] {
      val base64Encoded = encrypted.getBytes(StandardCharsets.UTF_8)
      val encryptedIvAndTextBytes = Base64.getDecoder.decode(base64Encoded)
      val ivSize = 16
      val iv = new Array[Byte](ivSize)
      System.arraycopy(encryptedIvAndTextBytes, 0, iv, 0, iv.length)
      val ivParameterSpec = new IvParameterSpec(iv)
      val encryptedSize = encryptedIvAndTextBytes.length - ivSize
      val encryptedBytes = new Array[Byte](encryptedSize)
      System.arraycopy(encryptedIvAndTextBytes, ivSize, encryptedBytes, 0, encryptedSize)
      val secretKeySpec = generateSecretKeySpec(password, salt)
      val cipher = Cipher.getInstance(CIPHER_SUITE)
      cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)
      val decrypted = cipher.doFinal(encryptedBytes)
      new String(decrypted, StandardCharsets.UTF_8)
  }.leftMap(e => HmacKeyDecryptionError(e.getMessage))

  private def generateSecretKeySpec(password: String, salt: String) = {
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
    val spec = new PBEKeySpec(password.toCharArray, Base64.getDecoder.decode(salt), ITERATIONS, KEY_LENGTH)
    val tmp = factory.generateSecret(spec)
    new SecretKeySpec(tmp.getEncoded, ENCRYPTION_TYPE)
  }
}

