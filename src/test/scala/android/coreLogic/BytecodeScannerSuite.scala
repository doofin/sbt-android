package android

import java.io.{File, FileOutputStream}
import java.nio.file.{Files, Path}
import java.util.jar.{JarEntry, JarOutputStream}

import org.objectweb.asm.{ClassWriter, Opcodes}
import scala.collection.JavaConverters._

class BytecodeScannerSuite extends munit.FunSuite {
  private def withTempDir(f: Path => Unit): Unit = {
    val dir = Files.createTempDirectory("bytecode-scanner-test")
    try f(dir)
    finally {
      val paths = Files.walk(dir)
      try paths.iterator().asScala.toList.reverse.foreach(Files.deleteIfExists(_))
      finally paths.close()
    }
  }

  private def classBytes(name: String, native: Boolean, reference: Boolean): Array[Byte] = {
    val writer = new ClassWriter(0)
    writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null)
    if (native) {
      writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_NATIVE, "nativeOne", "()V", null, null).visitEnd()
      writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_NATIVE, "nativeTwo", "()V", null, null).visitEnd()
    }
    val method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "()V", null, null)
    method.visitCode()
    if (reference) {
      method.visitTypeInsn(Opcodes.NEW, "cached/Target")
      method.visitInsn(Opcodes.POP)
    }
    method.visitInsn(Opcodes.RETURN)
    method.visitMaxs(1, 0)
    method.visitEnd()
    writer.visitEnd()
    writer.toByteArray
  }

  private def writeJar(path: Path, entries: (String, Array[Byte])*): File = {
    val jar = new JarOutputStream(new FileOutputStream(path.toFile))
    try entries.foreach { case (name, bytes) =>
      jar.putNextEntry(new JarEntry(name))
      jar.write(bytes)
      jar.closeEntry()
    }
    finally jar.close()
    path.toFile
  }

  test("NativeFinder searches nested classes and deduplicates native methods") {
    withTempDir { dir =>
      val nested = Files.createDirectories(dir.resolve("nested"))
      Files.write(nested.resolve("Native.class"), classBytes("example/Native", native = true, reference = false))
      Files.write(dir.resolve("Plain.class"), classBytes("example/Plain", native = false, reference = false))
      Files.write(dir.resolve("ignored.txt"), Array[Byte](1, 2))
      assertEquals(NativeFinder(dir.toFile).toSet, Set("example.Native"))
      assertEquals(NativeFinder(dir.toFile).toSet, NativeFinderOld(dir.toFile).toSet)
    }
  }

  test("NativeFinder handles missing directories and reports malformed classes") {
    withTempDir { dir =>
      assertEquals(NativeFinder(dir.resolve("missing").toFile), Seq.empty[String])
      val broken = dir.resolve("Broken.class")
      Files.write(broken, Array[Byte](1, 2, 3))
      val error = intercept[IllegalArgumentException](NativeFinder(dir.toFile))
      assert(error.getMessage.contains("Broken.class"))
      assert(error.getCause != null)
    }
  }

  test("jar scanners ignore non-class entries and preserve old results") {
    withTempDir { dir =>
      val jar = writeJar(dir.resolve("input.jar"),
        "notes.txt" -> Array[Byte](1, 2, 3),
        "example/Caller.class" -> classBytes("example/Caller", native = false, reference = true),
        "cached/Target.class" -> classBytes("cached/Target", native = false, reference = true))
      assertEquals(MethodCounter(jar), 2)
      assertEquals(MethodCounter(jar), MethodCounterOld(jar))
      val references = ReferenceFinder(jar, Seq("cached/"))
      assertEquals(references, Seq("visitTypeInsn(187,cached/Target)"))
      assertEquals(references, ReferenceFinderOld(jar, Seq("cached/")))
      assertEquals(ReferenceFinder(jar, Seq("unrelated/")), Seq.empty[String])
    }
  }

  test("jar scanners handle empty jars and reject malformed class entries") {
    withTempDir { dir =>
      val empty = writeJar(dir.resolve("empty.jar"), "notes.txt" -> Array[Byte](1))
      assertEquals(MethodCounter(empty), 0)
      assertEquals(ReferenceFinder(empty, Seq("cached/")), Seq.empty[String])
      val broken = writeJar(dir.resolve("broken.jar"), "Broken.class" -> Array[Byte](1, 2, 3))
      intercept[Exception](MethodCounter(broken))
      intercept[Exception](ReferenceFinder(broken, Seq("cached/")))
    }
  }
}