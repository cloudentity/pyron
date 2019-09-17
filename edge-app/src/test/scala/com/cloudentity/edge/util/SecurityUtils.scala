package com.cloudentity.edge.util

import java.io.FileOutputStream
import java.nio.file.{Files, Paths}
import java.security._
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import java.util.Base64

trait SecurityUtils {

  def generateRsaKeyPair: KeyPair = {
    val gen = KeyPairGenerator.getInstance("RSA")
    gen.initialize(2048)
    gen.generateKeyPair
  }

  def generateRsaKeyPairTuple: (PublicKey, PrivateKey) = {
    val keyPair = generateRsaKeyPair
    (keyPair.getPublic, keyPair.getPrivate)
  }

  def saveKeys(keyPair: KeyPair, path: String, name: String): Unit = {
    val file = path + "/" + name
    writeToFile(file + ".key", keyPair.getPrivate.getEncoded)
    writeToFile(file + ".pub", keyPair.getPublic.getEncoded)
  }

  def loadPrivateKey(file: String): PrivateKey = {
    val bytes = readBytes(file)
    val ks = new PKCS8EncodedKeySpec(bytes)
    val kf = KeyFactory.getInstance("RSA")
    kf.generatePrivate(ks)
  }

  def loadPublicKey(file: String): PublicKey = {
    val bytes = readBytes(file)
    val ks = new X509EncodedKeySpec(bytes)
    val kf = KeyFactory.getInstance("RSA")
    kf.generatePublic(ks)
  }

  def toBase64(privateKey: PrivateKey): String = {
    val sb = new StringBuilder
    sb.append("-----BEGIN RSA PRIVATE KEY-----\n")
    sb.append(Base64.getEncoder.encodeToString(privateKey.getEncoded))
    sb.append("\n-----END RSA PRIVATE KEY-----\n")
    sb.toString
  }

  def toBase64(publicKey: PublicKey): String = {
    val sb = new StringBuilder
    sb.append("-----BEGIN RSA PUBLIC KEY-----\n")
    sb.append(Base64.getEncoder.encodeToString(publicKey.getEncoded))
    sb.append("\n-----END RSA PUBLIC KEY-----\n")
    sb.toString
  }

  def readBytes(file: String): Array[Byte] = {
    Files.readAllBytes(Paths.get(file))
  }

  def writeToFile(file: String, bytes: Array[Byte]) = {
    val out = new FileOutputStream(file)
    out.write(bytes)
    out.close()
  }

  def rsaSign(data: Array[Byte], privateKey: PrivateKey): Array[Byte] = {
    val instance = Signature.getInstance("SHA256withRSA")
    instance.initSign(privateKey)
    instance.update(data)
    instance.sign()
  }

}
